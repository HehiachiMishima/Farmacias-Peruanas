package oracle.simulacion.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import oracle.common.exception.ConflictoException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

/**
 * Interpreta el Excel de proyección y agrupa sus filas por RUTA.
 *
 * Columnas esperadas (sin distinguir mayúsculas ni tildes): COD./COD, RUTA,
 * CLUSTER, CHUTE, BULTOS PROMEDIO/BULTOS y, opcionalmente, UNIDADES
 * PROMEDIO/UNIDADES y LINEAS PROMEDIO/LINEAS.
 */
public final class SimulacionExcel {

  private static final int MAX_FILAS = 100_000;
  private static final String[] COLUMNAS_OBLIGATORIAS = {"RUTA", "CLUSTER", "CHUTE", "BULTOS"};

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
    String nombre = archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename().toLowerCase(Locale.ROOT);
    try (InputStream entrada = archivo.getInputStream()) {
      if (nombre.endsWith(".csv")) {
        return analizarCsv(entrada);
      }
      if (nombre.endsWith(".xlsx") || nombre.endsWith(".xls")) {
        return analizarWorkbook(entrada);
      }
      throw new ConflictoException("Formato no soportado. Usa un archivo .xlsx, .xls o .csv.");
    } catch (IOException e) {
      throw new ConflictoException("No se pudo leer el archivo: " + e.getMessage());
    }
  }

  private static Analisis analizarWorkbook(InputStream entrada) throws IOException {
    try (Workbook libro = WorkbookFactory.create(entrada)) {
      if (libro.getNumberOfSheets() == 0) {
        throw new ConflictoException("El archivo no contiene hojas.");
      }
      Sheet hoja = libro.getSheetAt(0);
      DataFormatter formato = new DataFormatter(Locale.ROOT);
      List<List<String>> filas = new ArrayList<>();
      for (Row fila : hoja) {
        if (fila == null) {
          continue;
        }
        List<String> valores = new ArrayList<>();
        int ultima = Math.max(fila.getLastCellNum(), 0);
        for (int c = 0; c < ultima; c++) {
          Cell celda = fila.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
          valores.add(valorCelda(celda, formato));
        }
        filas.add(valores);
        if (filas.size() > MAX_FILAS + 1) {
          throw new ConflictoException("El archivo supera el máximo de " + MAX_FILAS + " filas.");
        }
      }
      return agrupar(filas);
    }
  }

  private static Analisis analizarCsv(InputStream entrada) throws IOException {
    List<List<String>> filas = new ArrayList<>();
    try (BufferedReader lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
      String linea;
      while ((linea = lector.readLine()) != null) {
        if (linea.isBlank()) {
          continue;
        }
        filas.add(parsearLineaCsv(linea));
        if (filas.size() > MAX_FILAS + 1) {
          throw new ConflictoException("El archivo supera el máximo de " + MAX_FILAS + " filas.");
        }
      }
    }
    return agrupar(filas);
  }

  private static Analisis agrupar(List<List<String>> filas) {
    int indiceCabecera = primeraFilaConDatos(filas);
    if (indiceCabecera < 0) {
      throw new ConflictoException("La hoja está vacía.");
    }
    Map<String, Integer> columnas = mapearColumnas(filas.get(indiceCabecera));
    for (String columna : COLUMNAS_OBLIGATORIAS) {
      if (!columnas.containsKey(columna)) {
        throw new ConflictoException("Falta la columna " + columna + " en el archivo.");
      }
    }

    Map<String, FilaRuta> porRuta = new LinkedHashMap<>();
    BigDecimal totalBultos = BigDecimal.ZERO;
    for (int i = indiceCabecera + 1; i < filas.size(); i++) {
      List<String> fila = filas.get(i);
      String ruta = valor(fila, columnas.get("RUTA"));
      if (ruta.isBlank()) {
        continue;
      }
      BigDecimal bultos = decimal(valor(fila, columnas.get("BULTOS")));
      if (bultos.signum() <= 0) {
        continue;
      }
      String cluster = valor(fila, columnas.get("CLUSTER"));
      String chute = valor(fila, columnas.get("CHUTE"));
      if (cluster.isBlank()) {
        throw new ConflictoException("La fila " + (i + 1) + " no tiene CLUSTER.");
      }
      if (chute.isBlank()) {
        throw new ConflictoException("La fila " + (i + 1) + " no tiene CHUTE.");
      }
      BigDecimal unidades = decimal(valor(fila, columnas.get("UNIDADES")));
      BigDecimal lineas = decimal(valor(fila, columnas.get("LINEAS")));
      totalBultos = totalBultos.add(bultos);

      FilaRuta acumulada = porRuta.get(ruta);
      if (acumulada == null) {
        porRuta.put(ruta, new FilaRuta(ruta, cluster, chute, bultos, unidades, lineas, 1));
      } else {
        porRuta.put(
            ruta,
            new FilaRuta(
                ruta,
                acumulada.cluster(),
                acumulada.chute(),
                acumulada.bultos().add(bultos),
                acumulada.unidades().add(unidades),
                acumulada.lineas().add(lineas),
                acumulada.conteoCod() + 1));
      }
    }

    if (porRuta.isEmpty()) {
      throw new ConflictoException("El archivo no contiene rutas con bultos mayores a cero.");
    }
    return new Analisis(List.copyOf(porRuta.values()), totalBultos);
  }

  private static int primeraFilaConDatos(List<List<String>> filas) {
    for (int i = 0; i < Math.min(filas.size(), 10); i++) {
      if (filas.get(i).stream().anyMatch((valor) -> valor != null && !valor.isBlank())) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Lee una celda sin depender del formato de miles del libro. DataFormatter
   * convierte 1500 con formato "#,##0" en "1,500", que el parser decimal
   * interpretaría como 1.5. Las celdas numéricas se leen como número puro.
   */
  private static String valorCelda(Cell celda, DataFormatter formato) {
    if (celda == null) {
      return "";
    }
    CellType tipo = celda.getCellType();
    if (tipo == CellType.FORMULA) {
      tipo = celda.getCachedFormulaResultType();
    }
    if (tipo == CellType.NUMERIC) {
      return BigDecimal.valueOf(celda.getNumericCellValue())
          .stripTrailingZeros()
          .toPlainString();
    }
    return formato.formatCellValue(celda).trim();
  }

  private static Map<String, Integer> mapearColumnas(List<String> cabecera) {
    Map<String, Integer> columnas = new LinkedHashMap<>();
    for (int i = 0; i < cabecera.size(); i++) {
      String nombre = normalizar(cabecera.get(i));
      switch (nombre) {
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

  private static String valor(List<String> fila, Integer indice) {
    if (indice == null || indice < 0 || indice >= fila.size()) {
      return "";
    }
    String valor = fila.get(indice);
    return valor == null ? "" : valor.trim();
  }

  private static BigDecimal decimal(String texto) {
    if (texto == null || texto.isBlank()) {
      return BigDecimal.ZERO;
    }
    String limpio = texto.trim().replace(" ", "").replace("%", "");
    if (limpio.contains(",") && limpio.contains(".")) {
      limpio = limpio.replace(",", "");
    } else if (limpio.contains(",")) {
      limpio = limpio.replace(".", "").replace(",", ".");
    }
    try {
      return new BigDecimal(limpio).setScale(2, RoundingMode.HALF_UP);
    } catch (NumberFormatException e) {
      return BigDecimal.ZERO;
    }
  }

  private static String normalizar(String texto) {
    if (texto == null) {
      return "";
    }
    // Excel exporta CSV con BOM UTF-8; sin quitarlo, la primera cabecera
    // (normalmente RUTA) no se reconoce y el archivo se rechaza.
    String limpio = texto.replace("\uFEFF", "").trim();
    String sinTildes =
        Normalizer.normalize(limpio.toUpperCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
    return sinTildes.replaceAll("\\s+", " ");
  }

  /** Parser CSV mínimo con soporte de comillas y comas o punto y coma. */
  private static List<String> parsearLineaCsv(String linea) {
    char separador = linea.chars().filter((c) -> c == ';').count() > linea.chars().filter((c) -> c == ',').count()
        ? ';'
        : ',';
    List<String> valores = new ArrayList<>();
    StringBuilder actual = new StringBuilder();
    boolean entreComillas = false;
    for (int i = 0; i < linea.length(); i++) {
      char c = linea.charAt(i);
      if (c == '"') {
        if (entreComillas && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
          actual.append('"');
          i++;
        } else {
          entreComillas = !entreComillas;
        }
      } else if (c == separador && !entreComillas) {
        valores.add(actual.toString().trim());
        actual.setLength(0);
      } else {
        actual.append(c);
      }
    }
    valores.add(actual.toString().trim());
    return valores;
  }
}
