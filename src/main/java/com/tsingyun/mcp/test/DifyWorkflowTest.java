package com.tsingyun.mcp.test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;
//import org.junit.jupiter.api.Test; // 确保你使用的是JUnit 4，如果不是，请取消注释上面的行并使用JUnit 5的Test

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class DifyWorkflowTest {

    // --- 配置信息 ---
    private static final String DIFY_BASE_URL = "http://172.24.0.5"; // *** 修改为你的Dify实例地址 ***
    private static final String DIFY_UPLOAD_API_KEY = "Bearer app-ozdM1O9bRCqlzwsoq7xk0wIG"; // 替换为你的上传 API 密钥
    private static final String DIFY_WORKFLOW_API_KEY = "Bearer app-ozdM1O9bRCqlzwsoq7xk0wIG"; // 替换为你的工作流 API 密钥
    private static final String USER = "abc-123";
    // *** 根据Postman成功的例子，修改为.docx文件和对应的路径 ***
    private static final String FILE_NAME = "测试地址为.pdf";
    // 确保这个路径是文件实际存在的绝对路径。
    // 如果文件和你的java代码在同一个目录下，可以使用 Paths.get(FILE_NAME);
    // 如果文件在D盘的某个目录，应该使用如下格式（注意Windows路径的分隔符）：
    private static final Path FILE_PATH = Paths.get(FILE_NAME);


    // HTTP 客户端和 JSON 对象映射器
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testUploadAndRunDifyWorkflow() {
        System.out.println("开始执行 Dify 工作流测试...");

        // 1. 上传文件
        String fileId = uploadFile(FILE_PATH, USER);

        if (fileId != null) {
            System.out.println("文件上传成功，文件 ID: " + fileId);
            // 2. 文件上传成功，继续运行工作流
            JsonNode result = runWorkflow(fileId, USER, "blocking");
            System.out.println("工作流执行结果:");
            System.out.println(result.toPrettyString());
        } else {
            System.err.println("文件上传失败，无法执行工作流");
        }
        System.out.println("Dify 工作流测试结束.");
    }

    /**
     * 上传文件到 Dify API。
     * 对应 Python 中的 upload_file 函数。
     *
     * @param filePath 要上传的文件路径。
     * @param user     用户标识。
     * @return 上传成功后 Dify 返回的文件 ID，如果失败则返回 null。
     */
    private String uploadFile(Path filePath, String user) {
        // *** 修改为本地Dify实例的URL ***
        String uploadUrl = DIFY_BASE_URL + "/v1/files/upload";
        String boundary = UUID.randomUUID().toString();
        String contentTypeHeader = "multipart/form-data; boundary=" + boundary;

        try {
            if (!Files.exists(filePath)) {
                System.err.println("错误: 文件不存在于 " + filePath.toAbsolutePath());
                return null;
            }

            System.out.println("上传文件中...");

            // 构建 multipart/form-data 请求体
            ByteArrayOutputStream requestBody = new ByteArrayOutputStream();
            byte[] boundaryBytes = ("--" + boundary + "\r\n").getBytes();
            byte[] closingBoundaryBytes = ("--" + boundary + "--\r\n").getBytes();

            // 1. 添加文件部分
            requestBody.write(boundaryBytes);
            requestBody.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filePath.getFileName().toString() + "\"\r\n").getBytes());
            // *** 针对.docx文件，修改Content-Type ***
            requestBody.write(("Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document\r\n\r\n").getBytes());
            requestBody.write(Files.readAllBytes(filePath));
            requestBody.write("\r\n".getBytes());

            // 2. 添加用户部分
            requestBody.write(boundaryBytes);
            requestBody.write(("Content-Disposition: form-data; name=\"user\"\r\n\r\n").getBytes());
            requestBody.write(user.getBytes());
            requestBody.write("\r\n".getBytes());

            // 3. (可选) 添加文件类型部分 (Dify 内部处理类型)
            // 根据你的Postman curl，这个字段似乎可以省略。
            // 如果你的Dify实例/工作流需要这个字段，你可以取消注释下面这块，并设置适当的类型（例如"DOCUMENT"而不是"TXT"）
            // requestBody.write(boundaryBytes);
            // requestBody.write(("Content-Disposition: form-data; name=\"type\"\r\n\r\n").getBytes());
            // requestBody.write("DOCUMENT".getBytes()); // 建议改为DOCUMENT, 或根据Dify支持的类型来
            // requestBody.write("\r\n".getBytes());

            requestBody.write(closingBoundaryBytes);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("Authorization", DIFY_UPLOAD_API_KEY)
                    .header("Content-Type", contentTypeHeader)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody.toByteArray()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) { // 201 表示创建成功
                System.out.println("文件上传成功");
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                return jsonResponse.get("id").asText(); // 获取上传的文件 ID
            } else {
                System.err.println(String.format("文件上传失败，状态码: %d, 响应体: %s", response.statusCode(), response.body()));
                return null;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println(String.format("上传文件时发生错误: %s", e.getMessage()));
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 运行 Dify 工作流。
     *
     * @param fileId       上传成功的文件 ID。
     * @param user         用户标识。
     * @param responseMode 响应模式（例如 "blocking"）。
     * @return 工作流执行结果的 JSONNode，如果失败则返回包含错误信息的 JsonNode。
     */
    private JsonNode runWorkflow(String fileId, String user, String responseMode) {
        // *** 修改为本地Dify实例的URL ***
        String workflowUrl = DIFY_BASE_URL + "/v1/workflows/run";

        try {
            System.out.println("运行工作流...");

            // 构建 JSON 请求体 - 根据你提供的 curl 调整
            ObjectNode data = objectMapper.createObjectNode();
            ObjectNode inputs = objectMapper.createObjectNode();
            ArrayNode filesList = objectMapper.createArrayNode(); // <--- 更改为 filesList
            ObjectNode fileItem = objectMapper.createObjectNode(); // <--- 更改为 fileItem

            fileItem.put("transfer_method", "local_file");
            fileItem.put("upload_file_id", fileId);
            // fileItem.put("type", "document"); // <--- 根据你的 curl，这里不再需要 type 字段
            filesList.add(fileItem);

            inputs.set("files", filesList); // <--- 更改为 "files"
            data.set("inputs", inputs);
            data.put("response_mode", responseMode);
            data.put("user", user);

            String jsonBody = objectMapper.writeValueAsString(data);
            System.out.println("工作流请求体: " + jsonBody); // 打印请求体以便调试

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(workflowUrl))
                    .header("Authorization", DIFY_WORKFLOW_API_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("工作流执行成功");
                return objectMapper.readTree(response.body());
            } else {
                System.err.println(String.format("工作流执行失败，状态码: %d, 响应体: %s", response.statusCode(), response.body()));
                ObjectNode errorNode = objectMapper.createObjectNode();
                errorNode.put("status", "error");
                errorNode.put("message", String.format("Failed to execute workflow, status code: %d, response: %s", response.statusCode(), response.body()));
                return errorNode;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println(String.format("运行工作流时发生错误: %s", e.getMessage()));
            e.printStackTrace();
            ObjectNode errorNode = objectMapper.createObjectNode();
            errorNode.put("status", "error");
            errorNode.put("message", e.getMessage());
            return errorNode;
        }
    }
}