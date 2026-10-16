package com.sharedkitchen.module.ledger;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

/**
 * 账单 PDF 导出：封面汇总 + 支出构成饼图 + 每日趋势柱状图 + 分类统计表 + 全量流水表。
 * 图表用 Java2D 画成 PNG 内嵌（无前端依赖）；中文字体走系统 STSong/宋体，缺失回退 Helvetica。
 */
@Service
public class LedgerPdfService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public byte[] export(String title, LedgerService.MonthSummary summary,
                         List<LedgerService.LedgerEntryView> entries) throws Exception {
        Document doc = new Document(PageSize.A4, 36, 36, 40, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font h1 = new Font(cjkFont(), 20, Font.BOLD);
        Font h2 = new Font(cjkFont(), 14, Font.BOLD);
        Font text = new Font(cjkFont(), 10, Font.NORMAL);
        Font small = new Font(cjkFont(), 9, Font.NORMAL);
        Font accent = new Font(cjkFont(), 16, Font.BOLD, new java.awt.Color(0xE8842E));

        doc.add(new Paragraph("共享厨房 · 账单报告", h1));
        doc.add(new Paragraph(title + "    生成于 " + LocalDate.now(), small));
        doc.add(spacer(10));

        // 汇总
        PdfPTable cards = new PdfPTable(4);
        cards.setWidthPercentage(100);
        addCard(cards, "收入", summary.income(), text);
        addCard(cards, "支出", summary.expense(), text);
        addCard(cards, "退款", summary.refund(), text);
        addCard(cards, "结余", summary.balance(), text);
        doc.add(cards);
        doc.add(spacer(12));

        // 饼图：支出构成
        Map<String, Long> byCat = new java.util.LinkedHashMap<>();
        for (LedgerService.LedgerEntryView e : entries) {
            if ("EXPENSE".equals(e.type())) {
                byCat.merge(e.category(), e.amountFen(), Long::sum);
            }
        }
        if (!byCat.isEmpty()) {
            doc.add(new Paragraph("支出构成", h2));
            doc.add(spacer(4));
            Image pie = Image.getInstance(piePng(byCat));
            pie.scalePercent(65);
            pie.setAlignment(Element.ALIGN_CENTER);
            doc.add(pie);
            doc.add(spacer(6));
            PdfPTable legend = new PdfPTable(byCat.size());
            legend.setWidthPercentage(100);
            byCat.forEach((name, fen) -> {
                PdfPCell c = new PdfPCell(new Phrase(name + " ¥" + fen / 100.0, small));
                c.setBorder(PdfPCell.NO_BORDER);
                legend.addCell(c);
            });
            doc.add(legend);
            doc.add(spacer(12));
        }

        // 柱状图：每日支出趋势
        if (!summary.days().isEmpty()) {
            doc.add(new Paragraph("每日收支趋势", h2));
            doc.add(spacer(4));
            Image bars = Image.getInstance(barsPng(summary));
            bars.scalePercent(88);
            bars.setAlignment(Element.ALIGN_CENTER);
            doc.add(bars);
            doc.add(spacer(12));
        }

        // 流水表
        doc.add(new Paragraph("流水明细（" + entries.size() + " 条）", h2));
        doc.add(spacer(4));
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{15, 10, 20, 13, 8, 34});
        for (String h : List.of("日期", "类型", "分类", "金额", "支付", "备注")) {
            PdfPCell c = new PdfPCell(new Phrase(h, new Font(cjkFont(), 9, Font.BOLD)));
            c.setBackgroundColor(new java.awt.Color(0xFFF3E0));
            c.setPadding(5);
            table.addCell(c);
        }
        for (LedgerService.LedgerEntryView e : entries) {
            table.addCell(cell(fmtType(e.type()) + (e.currency() != null && !"CNY".equals(e.currency())
                    ? "(" + e.currency() + ")" : ""), small));
            table.addCell(cell(e.date(), small));
            table.addCell(cell(e.category(), small));
            table.addCell(cell((e.type().equals("EXPENSE") ? "-¥" : "+¥") + e.amountFen() / 100.0, small));
            table.addCell(cell(e.paymentMethod() == null ? "-" : e.paymentMethod(), small));
            String remark = e.remark() == null ? "" : e.remark();
            if (e.subItems() != null && !e.subItems().isEmpty()) {
                StringBuilder sb = new StringBuilder(remark);
                e.subItems().forEach(si -> sb.append("  ").append(si.name())
                        .append("¥").append(si.amountFen() / 100.0));
                remark = sb.toString();
            }
            table.addCell(cell(remark, small));
        }
        doc.add(table);

        doc.add(spacer(10));
        doc.add(new Paragraph("—— 由共享厨房自动生成 ——", new Font(cjkFont(), 9, Font.NORMAL)));
        doc.close();
        return out.toByteArray();
    }

    private BaseFont cjkFont() {
        try {
            return BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            try {
                return BaseFont.createFont();
            } catch (Exception e2) {
                throw new IllegalStateException("无可用字体", e2);
            }
        }
    }

    private String fmtType(String t) {
        return switch (t) {
            case "EXPENSE" -> "支出";
            case "INCOME" -> "收入";
            case "REFUND" -> "退款";
            default -> t;
        };
    }

    private void addCard(PdfPTable t, String label, long fen, Font f) {
        PdfPCell c = new PdfPCell();
        c.setPadding(8);
        c.addElement(new Phrase(label, new Font(cjkFont(), 9)));
        c.addElement(new Phrase("¥" + fen / 100.0, new Font(cjkFont(), 14, Font.BOLD)));
        t.addCell(c);
    }

    private PdfPCell cell(String s, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(s == null ? "" : s, f));
        c.setPadding(4);
        return c;
    }

    private Paragraph spacer(int pt) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(pt);
        return p;
    }

    /** 支出构成饼图 PNG */
    private byte[] piePng(Map<String, Long> byCat) throws Exception {
        int w = 460, h = 300;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        long total = byCat.values().stream().mapToLong(Long::longValue).sum();
        List<Color> palette = List.of(new Color(0xEFA63C), new Color(0xE8836F), new Color(0x5BA47C),
                new Color(0xE8A23D), new Color(0x7BA6C9), new Color(0xB08BC9), new Color(0xC96B6B));
        double start = 0;
        int i = 0;
        int cx = 150, cy = 150, r = 110;
        for (Map.Entry<String, Long> e : byCat.entrySet()) {
            double extent = 360.0 * e.getValue() / total;
            g.setColor(palette.get(i++ % palette.size()));
            g.fillArc(cx - r, cy - r, r * 2, r * 2, (int) -start, (int) -Math.ceil(extent) - 1);
            start += extent;
        }
        g.setColor(Color.WHITE);
        g.fillOval(cx - 55, cy - 55, 110, 110);
        g.setColor(new Color(0x3D3325));
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 16));
        String label = "¥" + total / 100.0;
        int tw = g.getFontMetrics().stringWidth(label);
        g.drawString(label, cx - tw / 2, cy + 6);
        // 图例（右侧）
        int ly = 40;
        i = 0;
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12));
        for (Map.Entry<String, Long> e : byCat.entrySet()) {
            g.setColor(palette.get(i++ % palette.size()));
            g.fillRect(300, ly, 14, 14);
            g.setColor(new Color(0x3D3325));
            g.drawString(String.format("%s ¥%.2f (%.0f%%)", e.getKey(), e.getValue() / 100.0,
                    100.0 * e.getValue() / total), 320, ly + 12);
            ly += 24;
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** 每日趋势柱状图 PNG */
    private byte[] barsPng(LedgerService.MonthSummary s) throws Exception {
        var days = s.days();
        int w = Math.max(500, days.size() * 26), h = 260;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        long maxV = 1;
        for (var d : days) {
            maxV = Math.max(maxV, Math.max(d.incomeFen(), d.expenseFen()));
        }
        int baseY = h - 40;
        int barW = 10, gap = 26;
        g.setColor(new Color(0x3D3325));
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(30, baseY, w - 10, baseY);
        int x = 40;
        for (var d : days) {
            if (d.incomeFen() > 0) {
                int bh = (int) (180.0 * d.incomeFen() / maxV);
                g.setColor(new Color(0x5BA47C));
                g.fillRect(x, baseY - bh, barW, bh);
            }
            if (d.expenseFen() > 0) {
                int bh = (int) (180.0 * d.expenseFen() / maxV);
                g.setColor(new Color(0xE8836F));
                g.fillRect(x + barW + 2, baseY - bh, barW, bh);
            }
            String dd = d.date().length() >= 10 ? d.date().substring(8) : d.date();
            g.setColor(new Color(0x8C7F6A));
            g.drawString(dd, x - 2, baseY + 16);
            x += gap;
        }
        // 图例
        g.setColor(new Color(0x5BA47C));
        g.fillRect(40, 12, 12, 12);
        g.setColor(new Color(0x3D3325));
        g.drawString("收入", 56, 23);
        g.setColor(new Color(0xE8836F));
        g.fillRect(100, 12, 12, 12);
        g.setColor(new Color(0x3D3325));
        g.drawString("支出", 116, 23);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
