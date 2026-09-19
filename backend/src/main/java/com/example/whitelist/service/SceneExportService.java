package com.example.whitelist.service;

import com.example.whitelist.common.BusinessException;
import com.example.whitelist.dto.CommandResponse;
import com.example.whitelist.dto.OptionItem;
import com.example.whitelist.entity.Scene;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class SceneExportService {
    private static final String[] HEADERS = {
            "命令行", "描述", "支持的视图", "下一级视图", "正则表达式", "厂商"
    };
    private static final int[] COLUMN_WIDTHS = {32, 40, 28, 22, 55, 14};
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SceneService sceneService;
    private final CommandService commandService;

    public SceneExportService(SceneService sceneService, CommandService commandService) {
        this.sceneService = sceneService;
        this.commandService = commandService;
    }

    public ExportArchive export(List<Long> requestedSceneIds) {
        Set<Long> sceneIds = new LinkedHashSet<>(requestedSceneIds);
        if (sceneIds.isEmpty()) {
            throw new BusinessException(400, "至少选择一个要导出的场景");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Set<String> usedFileNames = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Long sceneId : sceneIds) {
                Scene scene = sceneService.requireScene(sceneId);
                List<CommandResponse> commands = commandService.listByScene(sceneId);
                String entryName = uniqueEntryName(scene, usedFileNames);
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(createWorkbook(commands));
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new BusinessException(500, "生成场景导出文件失败");
        }

        String fileName = "场景命令导出_" + LocalDateTime.now().format(FILE_TIME) + ".zip";
        return new ExportArchive(fileName, output.toByteArray());
    }

    private byte[] createWorkbook(List<CommandResponse> commands) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("命令列表");
            sheet.setDisplayGridlines(false);
            sheet.createFreezePane(0, 1);

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle bodyStyle = createBodyStyle(workbook);
            CellStyle vendorStyle = createBodyStyle(workbook);
            vendorStyle.setAlignment(HorizontalAlignment.CENTER);
            CommandRichTextFormatter richTextFormatter = new CommandRichTextFormatter(workbook);

            Row header = sheet.createRow(0);
            header.setHeightInPoints(24);
            for (int column = 0; column < HEADERS.length; column++) {
                header.createCell(column).setCellValue(HEADERS[column]);
                header.getCell(column).setCellStyle(headerStyle);
                sheet.setColumnWidth(column, COLUMN_WIDTHS[column] * 256);
            }

            int rowIndex = 1;
            for (CommandResponse command : commands) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(32);
                setRichTextCell(row, command, bodyStyle, richTextFormatter);
                setCell(row, 1, command.description(), bodyStyle);
                setCell(row, 2, joinViews(command.currentViews()), bodyStyle);
                setCell(row, 3, command.targetView() == null ? "" : command.targetView().name(), bodyStyle);
                setCell(row, 4, command.expandedRegex(), bodyStyle);
                setCell(row, 5, "HUAWEI", vendorStyle);
            }

            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, HEADERS.length - 1));
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setBottomBorderColor(IndexedColors.DARK_BLUE.getIndex());
        return style;
    }

    private CellStyle createBodyStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private void setCell(Row row, int column, String value, CellStyle style) {
        row.createCell(column).setCellValue(value == null ? "" : value);
        row.getCell(column).setCellStyle(style);
    }

    private void setRichTextCell(
            Row row,
            CommandResponse command,
            CellStyle style,
            CommandRichTextFormatter formatter
    ) {
        Cell cell = row.createCell(0);
        cell.setCellValue(formatter.format(command.expressionHtml(), command.expressionText()));
        cell.setCellStyle(style);
    }

    private String joinViews(List<OptionItem> views) {
        return views.stream()
                .map(OptionItem::name)
                .sorted()
                .collect(Collectors.joining("、"));
    }

    private String uniqueEntryName(Scene scene, Set<String> usedFileNames) {
        String baseName = scene.getName().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (baseName.isBlank()) {
            baseName = "场景_" + scene.getId();
        }
        if (baseName.length() > 80) {
            baseName = baseName.substring(0, 80);
        }

        String entryName = baseName + ".xlsx";
        if (!usedFileNames.add(entryName)) {
            entryName = baseName + "_" + scene.getId() + ".xlsx";
            usedFileNames.add(entryName);
        }
        return entryName;
    }

    public record ExportArchive(String fileName, byte[] content) {
    }

    private static final class CommandRichTextFormatter {
        private static final Pattern TOKEN_PATTERN = Pattern.compile("(?s)<[^>]*>|[^<]+");
        private static final Pattern TAG_PATTERN = Pattern.compile("^<\\s*(/?)\\s*([a-zA-Z0-9]+)");

        private final XSSFWorkbook workbook;
        private final Map<TextStyle, XSSFFont> fonts = new HashMap<>();

        private CommandRichTextFormatter(XSSFWorkbook workbook) {
            this.workbook = workbook;
        }

        private XSSFRichTextString format(String html, String fallbackText) {
            List<StyledCharacter> characters = normalize(parse(html));
            String text = characters.stream()
                    .map(character -> String.valueOf(character.value()))
                    .collect(Collectors.joining());
            if (!text.equals(fallbackText)) {
                return new XSSFRichTextString(fallbackText == null ? "" : fallbackText);
            }

            XSSFRichTextString richText = new XSSFRichTextString(text);
            int start = 0;
            while (start < characters.size()) {
                TextStyle style = characters.get(start).style();
                int end = start + 1;
                while (end < characters.size() && characters.get(end).style().equals(style)) {
                    end++;
                }
                if (!style.isPlain()) {
                    richText.applyFont(start, end, fonts.computeIfAbsent(style, this::createFont));
                }
                start = end;
            }
            return richText;
        }

        private List<StyledCharacter> parse(String html) {
            List<StyledCharacter> characters = new ArrayList<>();
            if (html == null || html.isEmpty()) {
                return characters;
            }

            int boldDepth = 0;
            int italicDepth = 0;
            int strikeDepth = 0;
            Matcher tokenMatcher = TOKEN_PATTERN.matcher(html);
            while (tokenMatcher.find()) {
                String token = tokenMatcher.group();
                if (!token.startsWith("<")) {
                    String text = HtmlUtils.htmlUnescape(token).replace('\u00a0', ' ');
                    TextStyle style = new TextStyle(boldDepth > 0, italicDepth > 0, strikeDepth > 0);
                    for (int index = 0; index < text.length(); index++) {
                        characters.add(new StyledCharacter(text.charAt(index), style));
                    }
                    continue;
                }

                Matcher tagMatcher = TAG_PATTERN.matcher(token);
                if (!tagMatcher.find()) {
                    continue;
                }
                boolean closing = !tagMatcher.group(1).isEmpty();
                String tag = tagMatcher.group(2).toLowerCase();
                if (closing) {
                    switch (tag) {
                        case "b", "strong" -> boldDepth = Math.max(0, boldDepth - 1);
                        case "i", "em" -> italicDepth = Math.max(0, italicDepth - 1);
                        case "s", "strike", "del" -> strikeDepth = Math.max(0, strikeDepth - 1);
                        default -> { }
                    }
                    if (tag.equals("div") || tag.equals("p")) {
                        addCharacter(characters, '\n', boldDepth, italicDepth, strikeDepth);
                    }
                } else {
                    switch (tag) {
                        case "b", "strong" -> boldDepth++;
                        case "i", "em" -> italicDepth++;
                        case "s", "strike", "del" -> strikeDepth++;
                        case "br" -> addCharacter(characters, '\n', boldDepth, italicDepth, strikeDepth);
                        default -> { }
                    }
                }
            }
            return characters;
        }

        private void addCharacter(
                List<StyledCharacter> characters,
                char value,
                int boldDepth,
                int italicDepth,
                int strikeDepth
        ) {
            characters.add(new StyledCharacter(
                    value,
                    new TextStyle(boldDepth > 0, italicDepth > 0, strikeDepth > 0)
            ));
        }

        private List<StyledCharacter> normalize(List<StyledCharacter> source) {
            List<StyledCharacter> normalized = new ArrayList<>();
            for (StyledCharacter character : source) {
                char value = character.value();
                if (value == ' ' || value == '\t' || value == '\u000B' || value == '\f' || value == '\r') {
                    if (!normalized.isEmpty()) {
                        char previous = normalized.getLast().value();
                        if (previous != ' ' && previous != '\n') {
                            normalized.add(new StyledCharacter(' ', character.style()));
                        }
                    }
                    continue;
                }
                if (value == '\n') {
                    while (!normalized.isEmpty() && normalized.getLast().value() == ' ') {
                        normalized.removeLast();
                    }
                }
                normalized.add(character);
            }
            while (!normalized.isEmpty() && normalized.getFirst().value() <= ' ') {
                normalized.removeFirst();
            }
            while (!normalized.isEmpty() && normalized.getLast().value() <= ' ') {
                normalized.removeLast();
            }
            return normalized;
        }

        private XSSFFont createFont(TextStyle style) {
            XSSFFont font = workbook.createFont();
            font.setBold(style.bold());
            font.setItalic(style.italic());
            font.setStrikeout(style.strikeout());
            return font;
        }
    }

    private record StyledCharacter(char value, TextStyle style) {
    }

    private record TextStyle(boolean bold, boolean italic, boolean strikeout) {
        private boolean isPlain() {
            return !bold && !italic && !strikeout;
        }
    }
}
