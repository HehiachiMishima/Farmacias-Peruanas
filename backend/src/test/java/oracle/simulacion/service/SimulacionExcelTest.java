package oracle.simulacion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import oracle.common.exception.ConflictoException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class SimulacionExcelTest {

  @Test
  void agrupaPorRutaYSumaBultosUnidadesYLineas() throws Exception {
    byte[] contenido;
    try (XSSFWorkbook libro = new XSSFWorkbook();
        ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
      Sheet hoja = libro.createSheet("Proyección");
      String[] cabecera = {
        "COD.", "RUTA", "CLUSTER", "CHUTE", "BULTOS PROMEDIO", "UNIDADES PROMEDIO", "LÍNEAS PROMEDIO"
      };
      Row filaCabecera = hoja.createRow(0);
      for (int i = 0; i < cabecera.length; i++) {
        filaCabecera.createCell(i).setCellValue(cabecera[i]);
      }
      escribirFila(hoja, 1, "1", "3030", "CL1", "CHUTE 1", 10, 100, 20);
      escribirFila(hoja, 2, "2", "3030", "CL1", "CHUTE 1", 5, 50, 10);
      escribirFila(hoja, 3, "3", "3012", "CL2", "CHUTE 2", 8, 80, 16);
      libro.write(salida);
      contenido = salida.toByteArray();
    }

    MockMultipartFile archivo =
        new MockMultipartFile(
            "archivo",
            "proyeccion.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            contenido);

    SimulacionExcel.Analisis analisis = SimulacionExcel.analizar(archivo);

    assertEquals(2, analisis.rutas().size());
    assertEquals("23.00", analisis.totalBultos().toPlainString());

    SimulacionExcel.FilaRuta ruta3030 =
        analisis.rutas().stream()
            .filter((ruta) -> ruta.codigoRuta().equals("3030"))
            .findFirst()
            .orElseThrow();
    assertEquals("15.00", ruta3030.bultos().toPlainString());
    assertEquals("150.00", ruta3030.unidades().toPlainString());
    assertEquals("30.00", ruta3030.lineas().toPlainString());
    assertEquals(2, ruta3030.conteoCod());
  }

  @Test
  void aceptaCsvConEncabezadosSinTildes() {
    String csv =
        "COD,RUTA,CLUSTER,CHUTE,BULTOS,UNIDADES,LINEAS\n"
            + "1,3030,CL1,CHUTE 1,12,120,24\n"
            + "2,3031,CL1,CHUTE 1,8,80,16\n";
    MockMultipartFile archivo =
        new MockMultipartFile("archivo", "proyeccion.csv", "text/csv", csv.getBytes());

    SimulacionExcel.Analisis analisis = SimulacionExcel.analizar(archivo);

    assertEquals(2, analisis.rutas().size());
    assertEquals("20.00", analisis.totalBultos().toPlainString());
  }

  @Test
  void aceptaCsvExportadoPorExcelConBom() {
    String csv =
        "\uFEFFRUTA,CLUSTER,CHUTE,BULTOS\n"
            + "3030,CL1,CHUTE 1,12\n";
    MockMultipartFile archivo =
        new MockMultipartFile("archivo", "proyeccion.csv", "text/csv", csv.getBytes());

    SimulacionExcel.Analisis analisis = SimulacionExcel.analizar(archivo);

    assertEquals(1, analisis.rutas().size());
    assertEquals("12.00", analisis.totalBultos().toPlainString());
  }

  @Test
  void noConfundeElSeparadorDeMilesConDecimales() throws Exception {
    byte[] contenido;
    try (XSSFWorkbook libro = new XSSFWorkbook();
        ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
      Sheet hoja = libro.createSheet("Proyección");
      Row cabecera = hoja.createRow(0);
      String[] columnas = {"COD.", "RUTA", "CLUSTER", "CHUTE", "BULTOS PROMEDIO"};
      for (int i = 0; i < columnas.length; i++) {
        cabecera.createCell(i).setCellValue(columnas[i]);
      }
      Row fila = hoja.createRow(1);
      fila.createCell(0).setCellValue(1);
      fila.createCell(1).setCellValue("3030");
      fila.createCell(2).setCellValue("CL1");
      fila.createCell(3).setCellValue("CHUTE 1");
      var estilo = libro.createCellStyle();
      estilo.setDataFormat(libro.createDataFormat().getFormat("#,##0"));
      var bultos = fila.createCell(4);
      bultos.setCellValue(1500);
      bultos.setCellStyle(estilo);
      libro.write(salida);
      contenido = salida.toByteArray();
    }

    MockMultipartFile archivo =
        new MockMultipartFile(
            "archivo",
            "proyeccion.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            contenido);

    SimulacionExcel.Analisis analisis = SimulacionExcel.analizar(archivo);

    assertEquals("1500.00", analisis.rutas().get(0).bultos().toPlainString());
    assertEquals("1500.00", analisis.totalBultos().toPlainString());
  }

  @Test
  void interpretaMilesConComaEnCsvConPuntoYComa() {
    String csv = "RUTA;CLUSTER;CHUTE;BULTOS\n3030;CL1;CHUTE 1;1,500\n3031;CL1;CHUTE 1;\"12,5\"\n";

    SimulacionExcel.Analisis analisis = SimulacionExcel.analizar(csv(csv));

    assertEquals("1500.00", analisis.rutas().get(0).bultos().toPlainString());
    assertEquals("12.50", analisis.rutas().get(1).bultos().toPlainString());
  }

  @Test
  void rechazaValoresNoNumericosIndicandoLaFila() {
    String csv = "RUTA,CLUSTER,CHUTE,BULTOS\n3030,CL1,CHUTE 1,doce\n";

    ConflictoException error =
        assertThrows(ConflictoException.class, () -> SimulacionExcel.analizar(csv(csv)));

    assertTrue(error.getMessage().contains("Fila 2"));
  }

  @Test
  void rechazaUnaRutaConDosClusters() {
    String csv = "RUTA,CLUSTER,CHUTE,BULTOS\n3030,CL1,CHUTE 1,5\n3030,CL2,CHUTE 1,5\n";

    assertThrows(ConflictoException.class, () -> SimulacionExcel.analizar(csv(csv)));
  }

  @Test
  void cuentaCodsDistintosYAceptaComillasConSaltoDeLinea() {
    String csv =
        "COD,RUTA,CLUSTER,CHUTE,BULTOS\n"
            + "A1,3030,\"CL\n1\",CHUTE 1,5\n"
            + "A1,3030,\"CL\n1\",CHUTE 1,5\n"
            + "A2,3030,\"CL\n1\",CHUTE 1,5\n";

    SimulacionExcel.Analisis analisis = SimulacionExcel.analizar(csv(csv));

    assertEquals(1, analisis.rutas().size());
    assertEquals(2, analisis.rutas().get(0).conteoCod());
    assertEquals("15.00", analisis.totalBultos().toPlainString());
  }

  @Test
  void ignoraColumnasMasAllaDelTope() throws Exception {
    byte[] contenido;
    try (XSSFWorkbook libro = new XSSFWorkbook();
        ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
      Sheet hoja = libro.createSheet("Proyección");
      Row cabecera = hoja.createRow(0);
      String[] columnas = {"RUTA", "CLUSTER", "CHUTE", "BULTOS"};
      for (int i = 0; i < columnas.length; i++) {
        cabecera.createCell(i).setCellValue(columnas[i]);
      }
      Row fila = hoja.createRow(1);
      fila.createCell(0).setCellValue("3030");
      fila.createCell(1).setCellValue("CL1");
      fila.createCell(2).setCellValue("CHUTE 1");
      fila.createCell(3).setCellValue(7);
      fila.createCell(16_383).setCellValue("XFD");
      libro.write(salida);
      contenido = salida.toByteArray();
    }

    SimulacionExcel.Analisis analisis = SimulacionExcel.analizar(xlsx(contenido));

    assertEquals("7.00", analisis.totalBultos().toPlainString());
  }

  @Test
  void rechazaUnXlsxQueNoEsUnLibro() {
    MockMultipartFile falso =
        new MockMultipartFile("archivo", "proyeccion.xlsx", "application/octet-stream", "hola".getBytes());

    assertThrows(ConflictoException.class, () -> SimulacionExcel.analizar(falso));
  }

  private static MockMultipartFile csv(String contenido) {
    return new MockMultipartFile("archivo", "proyeccion.csv", "text/csv", contenido.getBytes());
  }

  private static MockMultipartFile xlsx(byte[] contenido) {
    return new MockMultipartFile(
        "archivo",
        "proyeccion.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        contenido);
  }

  private static void escribirFila(
      Sheet hoja,
      int indice,
      String cod,
      String ruta,
      String cluster,
      String chute,
      double bultos,
      double unidades,
      double lineas) {
    Row fila = hoja.createRow(indice);
    fila.createCell(0).setCellValue(cod);
    fila.createCell(1).setCellValue(ruta);
    fila.createCell(2).setCellValue(cluster);
    fila.createCell(3).setCellValue(chute);
    fila.createCell(4).setCellValue(bultos);
    fila.createCell(5).setCellValue(unidades);
    fila.createCell(6).setCellValue(lineas);
  }
}
