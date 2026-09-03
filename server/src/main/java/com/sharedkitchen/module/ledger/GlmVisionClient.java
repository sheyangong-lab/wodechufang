package com.sharedkitchen.module.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sharedkitchen.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智谱 GLM 视觉模型客户端（账本 AI 识别小票/账单用）。
 * 走官方 OpenAI 风格 /chat/completions，图片以 base64 data URL 内联（服务端在内网也能用，无需公网回调）。
 * Key 未配置时识别接口报明确错误，不影响其他功能。
 */
@Component
public class GlmVisionClient {

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final long timeoutMs;
    private final ObjectMapper objectMapper;
    private final HttpClient http;

    public GlmVisionClient(@Value("${glm.api-key:}") String apiKey,
                           @Value("${glm.model:glm-4.6v}") String model,
                           @Value("${glm.base-url:https://open.bigmodel.cn/api/paas/v4}") String baseUrl,
                           @Value("${glm.timeout-ms:60000}") long timeoutMs,
                           ObjectMapper objectMapper) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.timeoutMs = timeoutMs;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public boolean enabled() {
        return !apiKey.isEmpty();
    }

    /**
     * 发送一张图片 + 提示词，返回模型文本回复。
     *
     * @param imageBase64 纯 base64（不含 data: 前缀）
     * @param mimeType    image/jpeg / image/png / image/webp
     */
    public String chat(String prompt, String imageBase64, String mimeType) {
        if (!enabled()) {
            throw new BusinessException("未配置 GLM API Key（服务端环境变量 GLM_API_KEY）");
        }
        Map<String, Object> imageUrl = new LinkedHashMap<>();
        imageUrl.put("url", "data:" + mimeType + ";base64," + imageBase64);
        Map<String, Object> imagePart = new LinkedHashMap<>();
        imagePart.put("type", "image_url");
        imagePart.put("image_url", imageUrl);
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("type", "text");
        textPart.put("text", prompt);

        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", List.of(imagePart, textPart));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(userMessage));
        body.put("temperature", 0.1);
        // 账本识别是简单抽取任务，关闭深度思考：实测 11~16s → 3s 内
        body.put("thinking", Map.of("type", "disabled"));
        // 部分网关支持 response_format；不支持的模型会忽略，提示词里同时强制 JSON
        body.put("response_format", Map.of("type", "json_object"));

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new BusinessException("识别请求构建失败");
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        String respBody;
        try {
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                String reason = upstreamError(resp.body());
                if (reason != null) {
                    throw new BusinessException("AI 识别失败：" + reason);
                }
                throw new BusinessException("AI 识别服务异常(" + resp.statusCode() + ")，请稍后重试");
            }
            respBody = resp.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("AI 识别连接超时，请重试");
        }

        try {
            JsonNode root = objectMapper.readTree(respBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new BusinessException("AI 没有返回识别结果，请重试");
            }
            return content.asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("AI 识别结果解析失败");
        }
    }

    /** 解析小票识别 Prompt，返回 items 内含 */
    public static final String RECEIPT_PROMPT = """
            你是记账助手。请仔细识别这张图片（购物小票/账单/价签/菜品照片）中的每一个消费项目。
            输出严格的 JSON，格式如下，不要输出任何其他文字、解释或 markdown 代码块标记：
            {"items":[{"name":"项目名称","price":12.5}]}
            要求：
            1. price 是该项目金额，单位元，数字类型，不含货币符号；
            2. 每个可辨认的商品/项目单独一项，名称尽量简短（不超过20字）；
            3. 金额或名称无法辨认的项目必须跳过，绝对不要猜测或编造金额；
            4. 图中可能有黑色遮挡区域，被遮挡的内容视为不存在，不要推断；
            5. 最多输出 20 项；没有可识别项目时输出 {"items":[]}。
            """;

    /** 从上游错误响应中提取可读原因（如「余额不足…」），取不到返回 null。 */
    private String upstreamError(String body) {
        try {
            String msg = objectMapper.readTree(body == null ? "" : body)
                    .path("error").path("message").asText("");
            return msg.isBlank() ? null : msg.substring(0, Math.min(msg.length(), 60));
        } catch (Exception e) {
            return null;
        }
    }

    /** 从模型回复中提取 items 数组（容错 markdown 代码块、前后杂文）。 */
    public static List<RecognizedItem> parseItems(String content, ObjectMapper objectMapper) {
        String json = content == null ? "" : content.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BusinessException("AI 未识别出可用内容");
        }
        json = json.substring(start, end + 1);
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.path("items");
            List<RecognizedItem> list = new ArrayList<>();
            if (!items.isArray()) return list;
            for (JsonNode it : items) {
                String name = it.path("name").asText("").trim();
                if (name.isEmpty()) continue;
                double yuan;
                try {
                    // asDouble 对数字直接取值，对 "12.5" 这类文本也能解析；失败/负数按 0 跳过
                    yuan = it.path("price").asDouble(Double.NaN);
                } catch (Exception e) {
                    continue;
                }
                if (Double.isNaN(yuan) || yuan <= 0 || !Double.isFinite(yuan)) continue;
                long fen = Math.round(yuan * 100);
                if (fen <= 0) continue;
                if (name.length() > 20) name = name.substring(0, 20);
                list.add(new RecognizedItem(name, fen));
                if (list.size() >= 20) break;
            }
            return list;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("AI 识别结果解析失败");
        }
    }

    public record RecognizedItem(String name, long amountFen) {}
}
