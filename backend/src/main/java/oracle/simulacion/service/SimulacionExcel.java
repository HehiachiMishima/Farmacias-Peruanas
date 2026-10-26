package oracle.simulacion.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import oracle.common.exception.ConflictoException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Interpreta el Excel de proyección y agrupa sus filas por RUTA.
 *
 * Columnas esperadas (sin distinguir mayúsculas ni tildes): COD./COD, RUTA,
 * CLUSTER, CHUTE, BULTOS PROMEDIO/BULTOS y, opcionalmente, UNIDADES
 * PROMEDIO/UNIDADES y LINEAS PROMEDIO/LINEAS.
 *
 * Los .xlsx se leen en streaming (SAX) fila por fila: la memoria depende del
 * número de rutas, no del tamaño del libro. Las filas y columnas tienen tope
 * para que un archivo malicioso (celdas en XFD, millones de filas) no pueda
 * agotar la memoria del servidor.
 */
public final class SimulacionExcel {

  static final int MAX_FILAS = 100_000;
  /** Solo se leen las primeras columnas: el formato usa 7. */
  static final int MAX_COLUMNAS = 50;
  /** Longitud de codigo_ruta y de los nombres de cluster/chute en la BD. */
  static final int MAX_TEXTO = 50;
  /** Máximo de DECIMAL(12,2). */
  static final BigDecimal MAX_VALOR = new BigDecimal("9999999999.99");
  /** Tope de una parte XML descomprimida (protección contra zip bombs). */
  private static final long MAX_PARTE_XML = 200L * 1024 * 1024;
  private static final int FILAS_BUSCAR_CABECERA = 10;
  private static final String[] COLUMNAS_OBLIGATORIAS = {"RUTA", "CLUSTER", "CHUTE", "BULTOS"};
  private static final Pattern MILES_COMA = Pattern.compile("^\\d{1,3}(,\\d{3})+$");
  private static final Pattern MILES_PUNTO = Pattern.compile("^\\d{1,3}(\\.\\d{3}){2,}$");
  private static final Pattern NUMERICO = Pattern.compile("^[0-9.,]+$");

  static {
    ZipSecureFile.setMaxEntrySize(MAX_PARTE_XML);
  }

  private SimulacionExcel() {}

  public record FilaRuta(
      String codigoRuta,
      String cluster,
      String chute,
      BigDecimal bultos,
      BigDecimal unidades,
      BigDecimal lineas,
      int conteoCod) {}

  public record Analisis(List<FilaRuta> rutas, BigDecimal totalBultos) {}

  public static Analisis analizar(MultipartFile archivo) {
    String nombre =
        archivo.getOriginalFilename() == null
            ? ""
            : archivo.getOriginalFilename().toLowerCase(Locale.ROOT);
    try {
      byte[] firma = firma(archivo);
      if (nombre.endsWith(".csv")) {
        exigir(esTexto(firma), "El archivo .csv no es de texto.");
        try (InputStream entrada = archivo.getInputStream()) {
          return analizarCsv(entrada);
        }
      }
      if (nombre.endsWith(".xlsx")) {
        exigir(empiezaCon(firma, 0x50, 0x4B, 0x03, 0x04), "El archivo .xlsx no es un libro de Excel válido.");
        return analizarXlsx(archivo);
      }
      if (nombre.endsWith(".xls")) {
        exigir(
            empiezaCon(firma, 0xD0, 0xCF, 0x11, 0xE0), "El archivo .xls no es un libro de Excel válido.");
        try (InputStream entrada = archivo.getInputStream()) {
          return analizarXls(entrada);
        }
      }
      throw new ConflictoException("Formato no soportado. Usa un archivo .xlsx, .xls o .csv.");
    } catch (IOException e) {
      throw new ConflictoException("No se pudo leer el archivo: " + e.getMessage());
    }
  }

  /**
   * Clave de comparación de nombres (cluster, chute, ruta): mayúsculas, sin
   * tildes ni espacios repetidos. Equivale a la colación utf8mb4_unicode_ci
   * de la BD, así que dos nombres con la misma clave son el mismo registro.
   */
  public static String clave(String texto) {
    if (texto == null) {
      return "";
    }
    String limpio = texto.replace("﻿", "").trim();
    String sinTildes =
        Normalizer.normalize(limpio.toUpperCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
    return sinTildes.replaceAll("\\s+", " ");
  }

  // ------------------------------------------------------------------
  // Lectores
  // ------------------------------------------------------------------

  /** .xlsx en streaming: el paquete se abre desde disco y la hoja se recorre con SAX. */
  private static Analisis analizarXlsx(MultipartFile archivo) throws IOException {
    Path temporal = Files.createTempFile("simulacion-", ".xlsx");
    try {
      archivo.transferTo(temporal);
      Agrupador agrupador = new Agrupador();
      try (OPCPackage paquete = OPCPackage.open(temporal.toFile(), PackageAccess.READ)) {
        XSSFReader lector = new XSSFReader(paquete);
        XSSFReader.SheetIterator hojas = (XSSFReader.SheetIterator) lector.getSheetsData();
        if (!hojas.hasNext()) {
          throw new ConflictoException("El archivo no contiene hojas.");
        }
        XMLReader parser = XMLHelper.newXMLReader();
        parser.setContentHandler(
            new XSSFSheetXMLHandler(
                lector.getStylesTable(),
                null,
                new ReadOnlySharedStringsTable(paquete),
                new FilasXlsx(agrupador),
                new FormatoCrudo(),
                false));
        try (InputStream hoja = hojas.next()) {
          parser.parse(new InputSource(hoja));
        }
      } catch (OpenXML4JException | SAXException | ParserConfigurationException e) {
        throw new ConflictoException("El archivo .xlsx está dañado o no es un libro de Excel.");
      }
      return agrupador.resultado();
    } finally {
      Files.deleteIfExists(temporal);
    }
  }

  /** .xls: el formato binario ya está acotado (65 536 filas × 256 columnas). */
  private static Analisis analizarXls(InputStream entrada) throws IOException {
    try (HSSFWorkbook libro = new HSSFWorkbook(entrada)) {
      if (libro.getNumberOfSheets() == 0) {
        throw new ConflictoException("El archivo no contiene hojas.");
      }
      Sheet hoja = libro.getSheetAt(0);
      Agrupador agrupador = new Agrupador();
      for (Row fila : hoja) {
        int ultima = Math.min(Math.max(fila.getLastCellNum(), 0), MAX_COLUMNAS);
        List<String> valores = new ArrayList<>(ultima);
        for (int c = 0; c < ultima; c++) {
          valores.add(valorCelda(fila.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)));
        }
        agrupador.fila(fila.getRowNum() + 1, valores);
      }
      return agrupador.resultado();
    }
  }

  /** CSV con separador , o ; (detectado en la cabecera) y comillas con saltos de línea. */
  private static Analisis analizarCsv(InputStream entrada) throws IOException {
    Agrupador agrupador = new Agrupador();
    try (BufferedReader lector =
        new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
      LectorCsv csv = new LectorCsv(lector);
      List<String> registro;
      while ((registro = csv.siguiente()) != null) {
        agrupador.fila(csv.linea(), registro);
      }
    }
    return agrupador.resultado();
  }

  // ------------------------------------------------------------------
  // Agrupación por ruta
  // ------------------------------------------------------------------

  /** Recibe las filas en orden, detecta la cabecera y acumula por RUTA. */
  private static final class Agrupador {

    private Map<String, Integer> columnas;
    private int filasVistas;
    private int filasDatos;
    private final Map<String, Acumulado> porRuta = new LinkedHashMap<>();
    private BigDecimal totalBultos = BigDecimal.ZERO;

    void fila(int numero, List<String> valores) {
      filasVistas++;
      if (columnas == null) {
        if (valores.stream().allMatch((valor) -> valor == null || valor.isBlank())) {
          if (filasVistas >= FILAS_BUSCAR_CABECERA) {
            throw new ConflictoException("No se encontró la cabecera en las primeras filas.");
          }
          return;
        }
        columnas = mapearColumnas(valores);
        for (String columna : COLUMNAS_OBLIGATORIAS) {
          if (!columnas.containsKey(columna)) {
            throw new ConflictoException("Falta la columna " + columna + " en el archivo.");
          }
        }
        return;
      }
      if (++filasDatos > MAX_FILAS) {
        throw new ConflictoException("El archivo supera el máximo de " + MAX_FILAS + " filas.");
      }
      acumular(numero, valores);
    }

    private void acumular(int numero, List<String> fila) {
      String ruta = valor(fila, "RUTA");
      if (ruta.isBlank()) {
        return;
      }
      BigDecimal bultos = decimal(valor(fila, "BULTOS"), numero, "BULTOS");
      if (bultos.signum() == 0) {
        return;
      }
      String cluster = valor(fila, "CLUSTER");
      String chute = valor(fila, "CHUTE");
      exigir(!cluster.isBlank(), "La fila " + numero + " no tiene CLUSTER.");
      exigir(!chute.isBlank(), "La fila " + numero + " no tiene CHUTE.");
      exigirLongitud(ruta, "RUTA", numero);
      exigirLongitud(cluster, "CLUSTER", numero);
      exigirLongitud(chute, "CHUTE", numero);
      BigDecimal unidades = decimal(valor(fila, "UNIDADES"), numero, "UNIDADES");
      BigDecimal lineas = decimal(valor(fila, "LINEAS"), numero, "LINEAS");
      String cod = valor(fila, "COD");

      Acumulado acumulado = porRuta.get(clave(ruta));
      if (acumulado == null) {
        acumulado = new Acumulado(ruta, cluster, chute);
        porRuta.put(clave(ruta), acumulado);
      } else {
        exigirIgual(acumulado.cluster, cluster, "CLUSTER", ruta, numero);
        exigirIgual(acumulado.chute, chute, "CHUTE", ruta, numero);
      }
      acumulado.sumar(bultos, unidades, lineas, cod, numero);
      totalBultos = totalBultos.add(bultos);
    }

    Analisis resultado() {
      if (columnas == null) {
        throw new ConflictoException("La hoja está vacía.");
      }
      if (porRuta.isEmpty()) {
        throw new ConflictoException("El archivo no contiene rutas con bultos mayores a cero.");
      }
      List<FilaRuta> rutas = new ArrayList<>(porRuta.size());
      porRuta.values().forEach((acumulado) -> rutas.add(acumulado.aFila()));
      return new Analisis(List.copyOf(rutas), totalBultos);
    }

    private String valor(List<String> fila, String columna) {
      Integer indice = columnas.get(columna);
      if (indice == null || indice >= fila.size()) {
        return "";
      }
      String valor = fila.get(indice);
      return valor == null ? "" : valor.trim();
    }
  }

  /** Totales de una ruta; los COD se cuentan sin repetir. */
  private static final class Acumulado {
    private final String ruta;
    private final String cluster;
    private final String chute;
    private BigDecimal bultos = BigDecimal.ZERO;
    private BigDecimal unidades = BigDecimal.ZERO;
    private BigDecimal lineas = BigDecimal.ZERO;
    private final Set<String> cods = new HashSet<>();
    private int filas;

    Acumulado(String ruta, String cluster, String chute) {
      this.ruta = ruta;
      this.cluster = cluster;
      this.chute = chute;
    }

    void sumar(BigDecimal b, BigDecimal u, BigDecimal l, String cod, int numero) {
      bultos = acotar(bultos.add(b), "BULTOS", numero);
      unidades = acotar(unidades.add(u), "UNIDADES", numero);
      lineas = acotar(lineas.add(l), "LINEAS", numero);
      if (!cod.isBlank()) {
        cods.add(clave(cod));
      }
      filas++;
    }

    FilaRuta aFila() {
      int conteo = cods.isEmpty() ? filas : cods.size();
      return new FilaRuta(ruta, cluster, chute, bultos, unidades, lineas, conteo);
    }

    private BigDecimal acotar(BigDecimal valor, String columna, int numero) {
      if (valor.compareTo(MAX_VALOR) > 0) {
        throw new ConflictoException(
            "La ruta " + ruta + " supera el máximo permitido de " + columna + " (fila " + numero + ").");
      }
      return valor;
    }
  }

  // ------------------------------------------------------------------
  // Utilidades de lectura
  // ------------------------------------------------------------------

  /** Recibe las celdas de la hoja .xlsx y entrega cada fila al agrupador. */
  private static final class FilasXlsx implements SheetContentsHandler {
    private final Agrupador agrupador;
    private final List<String> valores = new ArrayList<>();

    FilasXlsx(Agrupador agrupador) {
      this.agrupador = agrupador;
    }

    @Override
    public void startRow(int fila) {
      valores.clear();
    }

    @Override
    public void endRow(int fila) {
      agrupador.fila(fila + 1, valores);
    }

    @Override
    public void cell(String referencia, String valor, XSSFComment comentario) {
      int columna = referencia == null ? valores.size() : new CellReference(referencia).getCol();
      if (columna >= MAX_COLUMNAS) {
        return;
      }
      while (valores.size() < columna) {
        valores.add("");
      }
      valores.add(valor == null ? "" : valor.trim());
    }
  }

  /**
   * Las celdas numéricas se leen como número puro: con el formato del libro,
   * 1500 con "#,##0" llegaría como "1,500" y se confundiría con 1.5.
   */
  private static final class FormatoCrudo extends DataFormatter {
    FormatoCrudo() {
      super(Locale.ROOT);
    }

    @Override
    public String formatRawCellContents(double valor, int indice, String formato) {
      return crudo(valor);
    }

    @Override
    public String formatRawCellContents(
        double valor, int indice, String formato, boolean fechas1904) {
      return crudo(valor);
    }
  }

  private static String crudo(double valor) {
    return BigDecimal.valueOf(valor).stripTrailingZeros().toPlainString();
  }

  private static String valorCelda(Cell celda) {
    if (celda == null) {
      return "";
    }
    CellType tipo = celda.getCellType();
    if (tipo == CellType.FORMULA) {
      tipo = celda.getCachedFormulaResultType();
    }
    return switch (tipo) {
      case NUMERIC -> crudo(celda.getNumericCellValue());
      case STRING -> celda.getStringCellValue().trim();
      case BOOLEAN -> String.valueOf(celda.getBooleanCellValue());
      default -> "";
    };
  }

  /** Lector CSV por registros: respeta comillas (incluidos saltos de línea dentro). */
  private static final class LectorCsv {
    private final Reader lector;
    private final char separador;
    private int linea = 1;
    private int lineaRegistro;
    private boolean fin;

    LectorCsv(BufferedReader lector) throws IOException {
      this.lector = lector;
      lector.mark(64 * 1024);
      String cabecera = lector.readLine();
      lector.reset();
      this.separador = detectarSeparador(cabecera == null ? "" : cabecera);
    }

    int linea() {
      return lineaRegistro;
    }

    List<String> siguiente() throws IOException {
      while (!fin) {
        lineaRegistro = linea;
        List<String> valores = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean comillas = false;
        int c;
        while (true) {
          c = lector.read();
          if (c == -1) {
            fin = true;
            break;
          }
          if (c == '\n') {
            linea++;
          }
          if (comillas) {
            if (c == '"') {
              lector.mark(1);
              int siguiente = lector.read();
              if (siguiente == '"') {
                actual.append('"');
              } else {
                comillas = false;
                if (siguiente != -1) {
                  lector.reset();
                }
              }
            } else {
              actual.append((char) c);
            }
          } else if (c == '"') {
            comillas = true;
          } else if (c == separador) {
            agregar(valores, actual);
          } else if (c == '\n') {
            break;
          } else if (c != '\r') {
            actual.append((char) c);
          }
        }
        agregar(valores, actual);
        if (valores.stream().anyMatch((valor) -> !valor.isBlank())) {
          return valores;
        }
      }
      return null;
    }

    private void agregar(List<String> valores, StringBuilder actual) {
      if (valores.size() < MAX_COLUMNAS) {
        valores.add(actual.toString().trim());
      }
      actual.setLength(0);
    }

    private static char detectarSeparador(String cabecera) {
      long comas = cabecera.chars().filter((c) -> c == ',').count();
      long puntosComa = cabecera.chars().filter((c) -> c == ';').count();
      return puntosComa > comas ? ';' : ',';
    }
  }

  private static Map<String, Integer> mapearColumnas(List<String> cabecera) {
    Map<String, Integer> columnas = new HashMap<>();
    for (int i = 0; i < cabecera.size(); i++) {
      switch (clave(cabecera.get(i))) {
        case "COD", "COD." -> columnas.putIfAbsent("COD", i);
        case "RUTA" -> columnas.putIfAbsent("RUTA", i);
        case "CLUSTER" -> columnas.putIfAbsent("CLUSTER", i);
        case "CHUTE" -> columnas.putIfAbsent("CHUTE", i);
        case "BULTOS", "BULTOS PROMEDIO" -> columnas.putIfAbsent("BULTOS", i);
        case "UNIDADES", "UNIDADES PROMEDIO" -> columnas.putIfAbsent("UNIDADES", i);
        case "LINEAS", "LINEAS PROMEDIO" -> columnas.putIfAbsent("LINEAS", i);
        default -> {
          // Columna desconocida: se ignora.
        }
      }
    }
    return columnas;
  }

  /**
   * Convierte un texto numérico a DECIMAL(12,2). Acepta miles con coma
   * (1,500) o punto (1.500.000) y decimal con coma o punto; si aparecen
   * ambos, el último es el separador decimal. Rechaza textos no numéricos y
   * negativos indicando la fila, en lugar de convertirlos en 0 en silencio.
   */
  static BigDecimal decimal(String texto, int fila, String columna) {
    if (texto == null || texto.isBlank()) {
      return BigDecimal.ZERO;
    }
    String limpio = texto.trim().replace(" ", "").replace(" ", "");
    if (!NUMERICO.matcher(limpio).matches()) {
      throw new ConflictoException(
          "Fila " + fila + ": el valor \"" + texto.trim() + "\" de " + columna + " no es un número válido.");
    }
    int coma = limpio.lastIndexOf(',');
    int punto = limpio.lastIndexOf('.');
    if (coma >= 0 && punto >= 0) {
      char decimal = coma > punto ? ',' : '.';
      char miles = decimal == ',' ? '.' : ',';
      limpio = limpio.replace(String.valueOf(miles), "").replace(decimal, '.');
    } else if (coma >= 0) {
      limpio = MILES_COMA.matcher(limpio).matches() ? limpio.replace(",", "") : limpio.replace(',', '.');
    } else if (punto >= 0 && MILES_PUNTO.matcher(limpio).matches()) {
      limpio = limpio.replace(".", "");
    }
    try {
      BigDecimal valor = new BigDecimal(limpio).setScale(2, RoundingMode.HALF_UP);
      if (valor.compareTo(MAX_VALOR) > 0) {
        throw new ConflictoException("Fila " + fila + ": el valor de " + columna + " es demasiado grande.");
      }
      return valor;
    } catch (NumberFormatException e) {
      throw new ConflictoException(
          "Fila " + fila + ": el valor \"" + texto.trim() + "\" de " + columna + " no es un número válido.");
    }
  }

  private static void exigirLongitud(String texto, String columna, int fila) {
    if (texto.length() > MAX_TEXTO) {
      throw new ConflictoException(
          "Fila " + fila + ": " + columna + " supera los " + MAX_TEXTO + " caracteres.");
    }
  }

  private static void exigirIgual(String esperado, String recibido, String columna, String ruta, int fila) {
    if (!clave(esperado).equals(clave(recibido))) {
      throw new ConflictoException(
          "Fila " + fila + ": la ruta " + ruta + " ya tenía " + columna + " " + esperado
              + " y aparece con " + recibido + ". Una ruta debe tener un único " + columna + ".");
    }
  }

  private static void exigir(boolean condicion, String mensaje) {
    if (!condicion) {
      throw new ConflictoException(mensaje);
    }
  }

  private static byte[] firma(MultipartFile archivo) throws IOException {
    try (InputStream entrada = archivo.getInputStream()) {
      return entrada.readNBytes(512);
    }
  }

  private static boolean empiezaCon(byte[] datos, int... esperado) {
    if (datos.length < esperado.length) {
      return false;
    }
    for (int i = 0; i < esperado.length; i++) {
      if ((datos[i] & 0xFF) != esperado[i]) {
        return false;
      }
    }
    return true;
  }

  /** Un CSV es texto: sin bytes nulos (los binarios de Excel/zip los tienen). */
  private static boolean esTexto(byte[] datos) {
    for (byte dato : datos) {
      if (dato == 0) {
        return false;
      }
    }
    return true;
  }
}
