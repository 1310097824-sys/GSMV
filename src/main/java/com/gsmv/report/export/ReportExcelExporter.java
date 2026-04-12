package com.gsmv.report.export;

import com.gsmv.report.dto.DashboardSummary;
import com.gsmv.report.dto.EcosystemAnalyticsPoint;
import com.gsmv.report.dto.NameValuePoint;
import com.gsmv.report.dto.ObservationMapPoint;
import com.gsmv.report.dto.ReportExportSnapshot;
import com.gsmv.report.dto.SpeciesDistributionPoint;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class ReportExcelExporter {

    private ReportExcelExporter() {
    }

    public static byte[] export(ReportExportSnapshot snapshot) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);

            writeSummarySheet(workbook, headerStyle, snapshot.summary());
            writeNameValueSheet(workbook, headerStyle, "SpeciesProtection", "保护等级", snapshot.protectionLevelDistribution());
            writeNameValueSheet(workbook, headerStyle, "SpeciesIucn", "濒危状态", snapshot.iucnStatusDistribution());
            writeNameValueSheet(workbook, headerStyle, "SpeciesPhylum", "门", snapshot.speciesPhylumDistribution());
            writeNameValueSheet(workbook, headerStyle, "SpeciesClass", "纲", snapshot.speciesClassDistribution());
            writeNameValueSheet(workbook, headerStyle, "ObservationTrend", "日期", snapshot.observationTrend());
            writeNameValueSheet(workbook, headerStyle, "ObserverActivity", "观测人员", snapshot.observationActivityByUser());
            writeEcosystemSheet(workbook, headerStyle, snapshot.ecosystemAnalytics());
            writeSpeciesMapSheet(workbook, headerStyle, snapshot.speciesDistributionPoints());
            writeObservationMapSheet(workbook, headerStyle, snapshot.observationMapPoints());

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("导出 Excel 失败", exception);
        }
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle cellStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        cellStyle.setFont(font);
        return cellStyle;
    }

    private static void writeSummarySheet(Workbook workbook, CellStyle headerStyle, DashboardSummary summary) {
        Sheet sheet = workbook.createSheet("Summary");
        createHeader(sheet, headerStyle, "指标", "值");
        writePair(sheet, 1, "物种总数", summary.totalSpecies());
        writePair(sheet, 2, "观测次数", summary.totalObservations());
        writePair(sheet, 3, "生态系统数量", summary.totalEcosystems());
        writePair(sheet, 4, "活跃用户数", summary.totalUsers());
        writePair(sheet, 5, "近 7 天观测次数", summary.recentObservationCount());
        autoSize(sheet, 2);
    }

    private static void writeNameValueSheet(
            Workbook workbook,
            CellStyle headerStyle,
            String sheetName,
            String nameHeader,
            List<NameValuePoint> rows
    ) {
        Sheet sheet = workbook.createSheet(sheetName);
        createHeader(sheet, headerStyle, nameHeader, "数量");
        for (int index = 0; index < rows.size(); index++) {
            NameValuePoint row = rows.get(index);
            writePair(sheet, index + 1, row.name(), row.value());
        }
        autoSize(sheet, 2);
    }

    private static void writeEcosystemSheet(Workbook workbook, CellStyle headerStyle, List<EcosystemAnalyticsPoint> rows) {
        Sheet sheet = workbook.createSheet("EcosystemStats");
        createHeader(sheet, headerStyle, "生态系统", "类型", "观测次数", "发现物种数");
        for (int index = 0; index < rows.size(); index++) {
            EcosystemAnalyticsPoint row = rows.get(index);
            Row excelRow = sheet.createRow(index + 1);
            excelRow.createCell(0).setCellValue(nullSafe(row.ecosystemName()));
            excelRow.createCell(1).setCellValue(nullSafe(row.ecosystemType()));
            excelRow.createCell(2).setCellValue(row.observationCount());
            excelRow.createCell(3).setCellValue(row.speciesCount());
        }
        autoSize(sheet, 4);
    }

    private static void writeSpeciesMapSheet(Workbook workbook, CellStyle headerStyle, List<SpeciesDistributionPoint> rows) {
        Sheet sheet = workbook.createSheet("SpeciesMap");
        createHeader(sheet, headerStyle, "中文名", "学名", "纬度", "经度", "地理范围", "保护等级", "濒危状态");
        for (int index = 0; index < rows.size(); index++) {
            SpeciesDistributionPoint row = rows.get(index);
            Row excelRow = sheet.createRow(index + 1);
            excelRow.createCell(0).setCellValue(nullSafe(row.chineseName()));
            excelRow.createCell(1).setCellValue(nullSafe(row.scientificName()));
            excelRow.createCell(2).setCellValue(row.locationLat() == null ? "" : row.locationLat().toPlainString());
            excelRow.createCell(3).setCellValue(row.locationLng() == null ? "" : row.locationLng().toPlainString());
            excelRow.createCell(4).setCellValue(nullSafe(row.geoRangeText()));
            excelRow.createCell(5).setCellValue(nullSafe(row.protectionLevel()));
            excelRow.createCell(6).setCellValue(nullSafe(row.iucnStatus()));
        }
        autoSize(sheet, 7);
    }

    private static void writeObservationMapSheet(Workbook workbook, CellStyle headerStyle, List<ObservationMapPoint> rows) {
        Sheet sheet = workbook.createSheet("ObservationMap");
        createHeader(sheet, headerStyle, "地点", "生态系统", "观测人员", "观测时间", "纬度", "经度", "关联物种数", "备注");
        for (int index = 0; index < rows.size(); index++) {
            ObservationMapPoint row = rows.get(index);
            Row excelRow = sheet.createRow(index + 1);
            excelRow.createCell(0).setCellValue(nullSafe(row.locationName()));
            excelRow.createCell(1).setCellValue(nullSafe(row.ecosystemName()));
            excelRow.createCell(2).setCellValue(nullSafe(row.observerName()));
            excelRow.createCell(3).setCellValue(row.observedAt() == null ? "" : row.observedAt().toString());
            excelRow.createCell(4).setCellValue(row.locationLat() == null ? "" : row.locationLat().toPlainString());
            excelRow.createCell(5).setCellValue(row.locationLng() == null ? "" : row.locationLng().toPlainString());
            excelRow.createCell(6).setCellValue(row.speciesCount());
            excelRow.createCell(7).setCellValue(nullSafe(row.note()));
        }
        autoSize(sheet, 8);
    }

    private static void createHeader(Sheet sheet, CellStyle headerStyle, String... headers) {
        Row row = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(headerStyle);
        }
    }

    private static void writePair(Sheet sheet, int rowIndex, String name, long value) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(nullSafe(name));
        row.createCell(1).setCellValue(value);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static void autoSize(Sheet sheet, int columnCount) {
        for (int index = 0; index < columnCount; index++) {
            sheet.autoSizeColumn(index);
        }
    }
}
