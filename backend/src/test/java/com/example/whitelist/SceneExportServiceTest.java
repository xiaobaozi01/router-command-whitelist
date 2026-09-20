package com.example.whitelist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.whitelist.dto.CommandResponse;
import com.example.whitelist.dto.OptionItem;
import com.example.whitelist.entity.Scene;
import com.example.whitelist.service.CommandService;
import com.example.whitelist.service.SceneExportService;
import com.example.whitelist.service.SceneService;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SceneExportServiceTest {
    @Mock
    private SceneService sceneService;
    @Mock
    private CommandService commandService;
    @InjectMocks
    private SceneExportService exportService;

    @Test
    void shouldExportOneWorkbookForEachScene() throws Exception {
        Scene inspection = scene(1L, "日常巡检");
        Scene recovery = scene(2L, "故障恢复");
        when(sceneService.requireScene(1L)).thenReturn(inspection);
        when(sceneService.requireScene(2L)).thenReturn(recovery);
        when(commandService.listByScene(1L)).thenReturn(List.of(command()));
        when(commandService.listByScene(2L)).thenReturn(List.of());

        SceneExportService.ExportArchive archive = exportService.export(List.of(1L, 2L));

        assertThat(archive.fileName()).startsWith("场景命令导出_").endsWith(".zip");
        List<String> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive.content()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
                try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(zip.readAllBytes()))) {
                    var sheet = workbook.getSheetAt(0);
                    assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("命令行");
                    assertThat(sheet.getRow(0).getCell(5).getStringCellValue()).isEqualTo("厂商");
                    if (entry.getName().equals("日常巡检.xlsx")) {
                        assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("display version old all");
                        assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("查看版本");
                        assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("接口视图、系统视图");
                        assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("用户视图");
                        assertThat(sheet.getRow(1).getCell(4).getStringCellValue())
                                .isEqualTo("^(?:display (?:\\S+))$");
                        assertThat(sheet.getRow(1).getCell(5).getStringCellValue()).isEqualTo("HUAWEI");
                        assertCommandRichText((XSSFRichTextString) sheet.getRow(1).getCell(0).getRichStringCellValue());
                    }
                }
            }
        }
        assertThat(entries).containsExactly("日常巡检.xlsx", "故障恢复.xlsx");
    }

    private Scene scene(Long id, String name) {
        Scene scene = new Scene();
        scene.setId(id);
        scene.setName(name);
        return scene;
    }

    private CommandResponse command() {
        return new CommandResponse(
                1L,
                "<p><strong>display</strong> <em>version</em> <s>old</s> <strong><em><s>all</s></em></strong></p>",
                "display version old all", "查看版本", "display ${WORD}", true, true,
                "^(?:display (?:\\S+))$",
                List.of(new OptionItem(2L, "系统视图"), new OptionItem(1L, "接口视图")),
                new OptionItem(3L, "用户视图"), List.of(new OptionItem(1L, "日常巡检")),
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private void assertCommandRichText(XSSFRichTextString richText) {
        assertThat(richText.getString()).isEqualTo("display version old all");

        assertThat(richText.getFontAtIndex(0).getBold()).isTrue();
        assertThat(richText.getFontAtIndex(8).getItalic()).isTrue();
        assertThat(richText.getFontAtIndex(16).getStrikeout()).isTrue();

        var combinedFont = richText.getFontAtIndex(20);
        assertThat(combinedFont.getBold()).isTrue();
        assertThat(combinedFont.getItalic()).isTrue();
        assertThat(combinedFont.getStrikeout()).isTrue();
    }
}
