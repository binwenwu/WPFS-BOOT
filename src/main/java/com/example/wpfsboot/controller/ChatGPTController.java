package com.example.wpfsboot.controller;

import com.example.wpfsboot.common.Constants;
import com.example.wpfsboot.common.Result;
import com.example.wpfsboot.entity.GPTParams;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.finetune.FineTuneRequest;
import com.theokanning.openai.image.CreateImageEditRequest;
import com.theokanning.openai.image.CreateImageVariationRequest;
import com.theokanning.openai.image.ImageResult;
import com.theokanning.openai.moderation.ModerationRequest;
import io.github.asleepyfish.config.ChatGPTProperties;
import io.github.asleepyfish.entity.billing.Billing;
import io.github.asleepyfish.entity.billing.Subscription;
import io.github.asleepyfish.enums.audio.AudioResponseFormatEnum;
import io.github.asleepyfish.enums.edit.EditModelEnum;
import io.github.asleepyfish.enums.embedding.EmbeddingModelEnum;
import io.github.asleepyfish.enums.image.ImageResponseFormatEnum;
import io.github.asleepyfish.enums.image.ImageSizeEnum;
import io.github.asleepyfish.service.OpenAiProxyService;
import io.github.asleepyfish.util.OpenAiUtils;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;


import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @Author: 结束乐队
 * @Date: 2023/7/18
 */
@RestController
@RequestMapping("/wpfgpt")
public class ChatGPTController {

    @Value("${files.upload.path}")
    private String fileUploadPath;

    @Value("${server.ip}")
    private String serverIp;

    @Value("${server.port}")
    private String serverPort;


    @Value(("${server.password}"))
    private String serverPassword;


    @Value("${chatgpt.proxy-host}")
    private String proxyPort;

    @Autowired
    private OpenAiUtils openAiUtils;


    /**
     * @param question
     * @return
     */
    public static int containsCode(String question) {
        String[] keyword01 = {"图"};
        String[] keyword02 = {"报表"};

        for (String keyword : keyword01) {
            if (question.contains(keyword)) {
                return 1;
            }
        }

        for (String keyword : keyword02) {
            if (question.contains(keyword)) {
                return 2;
            }
        }

        return 3;
    }

    /**
     * @param question
     * @return
     */
    public static boolean containsJudge(String question) {
        String[] keywords = {"预处理后"};

        for (String keyword : keywords) {
            if (question.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @param originalString
     * @param startMarker
     * @param endMarker
     * @return
     */
    public static String extractBetweenMarkers(String originalString, String startMarker, String endMarker) {
        int startIndex = originalString.indexOf(startMarker);
        int endIndex = originalString.lastIndexOf(endMarker);

        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            // 使用substring方法提取两个标记之间的部分
            return originalString.substring(startIndex + startMarker.length(), endIndex);
        } else {
            return ""; // 如果没有找到匹配的标记则返回空字符串
        }
    }

    public static void writePythonCodeToFile(String pythonCode, String filePath) throws IOException {
        File file = new File(filePath);
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(file));
            writer.write(pythonCode);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private static final String IMAGE_PATH = "/home/wpfs/algorithm/submission75254/gpt_output/img/"; // 图片文件所在路径

    @GetMapping("/api/images/{fileName}")
    public ResponseEntity<byte[]> getImage(@PathVariable String fileName) {
        try {
            String imagePath = IMAGE_PATH + fileName.replace(".csv", ".png");
            System.out.println("imagePath: " + imagePath);
            byte[] imageData = readImageData(imagePath);
            if (imageData != null) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.IMAGE_JPEG); // 设置图片类型，可以根据实际情况调整
                return new ResponseEntity<>(imageData, headers, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND); // 图片不存在
            }
        } catch (Exception e) {
            // 处理异常
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // 根据图片路径读取图片数据的方法
    private byte[] readImageData(String imagePath) {
        try {
            Path path = Paths.get(imagePath);
            return Files.readAllBytes(path);
        } catch (IOException e) {
            // 处理读取文件异常
            e.printStackTrace();
            return null;
        }
    }


    private final String docxDirectory = "/home/wpfs/algorithm/submission75254/gpt_output/docx"; // 指定存放 .docx 文件的目录

    @GetMapping("/docx/{filename}")
    public ResponseEntity<Resource> downloadDocx(@PathVariable String filename) throws MalformedURLException {
        Path filePath = Paths.get(docxDirectory).resolve(filename);

        Resource resource = new UrlResource(filePath.toUri());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }


    /**
     * 问答
     *
     * @param gptParams 问题
     * @return 答案
     */
    @PostMapping("/postChat2")
    public ResponseEntity<Result> postChat2(@RequestBody GPTParams gptParams) {
        Result result = new Result();
        //时间戳
        String time = System.currentTimeMillis() + "";
        result.setTime(time);

        // 问题预处理
        String question = gptParams.getQuestion();
        question = question.replace("ROUND(A.WS,1)", "AWS").replace("ROUND(A.POWER,0)", "APOWER");
        String fileName = gptParams.getFileName();


        // 判断问题类型
        int tag1 = containsCode(question);
        boolean tag2 = containsJudge(question);
        String filePath = tag2 ? "/home/wpfs/algorithm/submission75254/outfile/" + fileName : "/home/wpfs/algorithm/submission75254/pred/" + fileName;


        if (tag1 == 3) {
            result.setImage(false);
            System.out.println(question);
            result.setMsg(OpenAiUtils.createChatCompletion(question).get(0));
        } else if (tag1 == 1) {
            // 判断是否要展示图片
            result.setImage(true);


            if (tag2) {
                question = question.replace("预处理后", "");
            } else {
                question = question.replace("预测后", "");
            }


            String text = "我有一个csv文件，位置在" + filePath + ",第一行是列名，第二行开始是数据，其中列名有：DATATIME,WINDSPEED,PREPOWER,WINDDIRECTION,TEMPERATURE,HUMIDITY,PRESSURE,AWS,APOWER,YD15,";
            question = text + question
                    + ", 图片输出至/home/wpfs/algorithm/submission75254/gpt_output/img/,"
                    + "图片名为:" + time + ".png,"
                    + "请给出完整的Python代码, 默认我已经安装了所有需要的包。且我只需要你返回给我一个可以执行的完整代码段。整个代码段用markdown中的```包围";
            //            question = text + question
//                    + ", 图片输出至/home/wpfs/algorithm/submission75254/gpt_output/img/,"
//                    + "图片名为:" + time + ".png,"
//                    + "请给出完整的Python代码, 默认我已经安装了所有需要的包。且我只需要你返回给我一个可以执行的完整代码段。并且注释部分用4个#作为前缀";

            System.out.println(question);
            result.setMsg(OpenAiUtils.createChatCompletion(question).get(0));
            System.out.println(result.getMsg());

            String pythonCode = extractBetweenMarkers(result.getMsg(), "```python", "```");
//            System.out.println(pythonCode);

            String pyFileName = fileName.replace(".csv", ".py");
            String pyFilePath = "/home/wpfs/algorithm/submission75254/gpt_output/py/" + pyFileName;

            try {
                writePythonCodeToFile(pythonCode, pyFilePath);
                System.out.println("Python file generated successfully at: " + pyFilePath);
            } catch (IOException e) {
                System.err.println("Error while generating Python file: " + e.getMessage());
            }

            String host = serverIp; // 远程服务器IP地址
            String user = "root"; // 远程服务器用户名
            String password = serverPassword; // 远程服务器密码
            // 要执行的命令
            StringBuilder command = new StringBuilder("conda activate py37;cd /home/wpfs/algorithm/submission75254/gpt_output/py/;python ./" + pyFileName + ";");


            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(user, host, 22); // 创建一个SSH会话
                session.setPassword(password); // 设置会话密码
                session.setConfig("StrictHostKeyChecking", "no"); // 设置会话配置,不检查HostKey
                session.connect(); // 连接会话

                Channel channel = session.openChannel("exec"); // 打开一个exec通道
                ((ChannelExec) channel).setCommand(command.toString()); // 设置要执行的命令
                channel.setInputStream(null);
                ((ChannelExec) channel).setErrStream(System.err); // 设置错误输出流

                InputStream inputStream = channel.getInputStream();
                channel.connect(); // 连接通道

                byte[] buffer = new byte[1024];
                while (true) {
                    while (inputStream.available() > 0) {
                        int i = inputStream.read(buffer, 0, 1024);
                        if (i < 0) {
                            break;
                        }
                        System.out.print(new String(buffer, 0, i)); // 输出结果到控制台
                    }
                    if (channel.isClosed()) {
                        if (inputStream.available() > 0) {
                            continue;
                        }
                        System.out.println("exit-status: " + channel.getExitStatus()); // 输出退出状态
                        break;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (Exception ee) {
                    } // 等待一秒钟
                }
                channel.disconnect(); // 断开通道
                session.disconnect(); // 断开会话
            } catch (Exception e) {
                e.printStackTrace(); // 输出错误信息
            }
        } else {
            // 判断是否要展示报表
            result.setReport(true);
            result.setMsg("报表生成完成");
            String tag;

            if (tag2) {
                question = question.replace("预处理后", "");
                tag = "outfile";
            } else {
                question = question.replace("预测后", "");
                tag = "pred";
            }

            // TODO 数据报表造假
            String host = serverIp; // 远程服务器IP地址
            String user = "root"; // 远程服务器用户名
            String password = serverPassword; // 远程服务器密码
            String mdFilePath = "/home/wpfs/algorithm/submission75254/gpt_output/markdown/" + fileName.replace(".csv", "_") + time + ".md";
            String docxFilePath = "/home/wpfs/algorithm/submission75254/gpt_output/docx/" + fileName.replace(".csv", "_") + time + ".docx";
            String pdfFilePath = "/home/wpfs/algorithm/submission75254/gpt_output/pdf/" + fileName.replace(".csv", "_") + time + ".pdf";

//            mdFilePath = "/home/wpfs/algorithm/submission75254/gpt_output/markdown/19_1691754450508.md";
//            docxFilePath = "/home/wpfs/algorithm/submission75254/gpt_output/docx/19_1691754450508.docx";
//            pdfFilePath = "/home/wpfs/algorithm/submission75254/gpt_output/pdf/19_1691754450508.pdf";

            // 要执行的命令
            StringBuilder command = new StringBuilder("conda activate py37;cd /home/wpfs/algorithm/submission75254/gpt_output/;python ./report.py --file_name " + fileName + " --time_stamp " + time + " --tag " + tag + ";" + "cd markdown;pandoc " + mdFilePath + " -o " + docxFilePath + ";");


            System.out.println("command: " + command.toString());
            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(user, host, 22); // 创建一个SSH会话
                session.setPassword(password); // 设置会话密码
                session.setConfig("StrictHostKeyChecking", "no"); // 设置会话配置,不检查HostKey
                session.connect(); // 连接会话

                Channel channel = session.openChannel("exec"); // 打开一个exec通道
                ((ChannelExec) channel).setCommand(command.toString()); // 设置要执行的命令
                channel.setInputStream(null);
                ((ChannelExec) channel).setErrStream(System.err); // 设置错误输出流

                InputStream inputStream = channel.getInputStream();
                channel.connect(); // 连接通道

                byte[] buffer = new byte[1024];
                while (true) {
                    while (inputStream.available() > 0) {
                        int i = inputStream.read(buffer, 0, 1024);
                        if (i < 0) {
                            break;
                        }
                        System.out.print(new String(buffer, 0, i)); // 输出结果到控制台
                    }
                    if (channel.isClosed()) {
                        if (inputStream.available() > 0) {
                            continue;
                        }
                        System.out.println("exit-status: " + channel.getExitStatus()); // 输出退出状态
                        break;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (Exception ee) {
                    } // 等待一秒钟
                }
                channel.disconnect(); // 断开通道
                session.disconnect(); // 断开会话
            } catch (Exception e) {
                e.printStackTrace(); // 输出错误信息
            }

        }

        result.setCode(Constants.CODE_200);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }


    /**
     * 问答
     *
     * @param question 问题
     * @return 答案
     */
    @PostMapping("/postChat")
    public ResponseEntity<Result> postChat(@RequestBody String question) {
        Result result = new Result();
        result.setMsg(OpenAiUtils.createChatCompletion(question).get(0));
        result.setCode(Constants.CODE_200);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    /**
     * 问答
     *
     * @param content 问题
     * @return 答案
     */
    @GetMapping("/getChat")
    public List<String> getChat(String content) {
        return OpenAiUtils.createChatCompletion(content);
    }

    /**
     * 流式问答，返回到控制台
     */
    @GetMapping("/streamChat")
    public void streamChat(String content) {
        // OpenAiUtils.createStreamChatCompletion(content, System.out);
        // 下面的默认和上面这句代码一样，是输出结果到控制台
        OpenAiUtils.createStreamChatCompletion(content);
    }

    /**
     * 流式问答，输出结果到WEB浏览器端
     */
    @GetMapping("/streamChatWithWeb")
    public void streamChatWithWeb(String content, HttpServletResponse response) throws IOException, InterruptedException {
        // 需要指定response的ContentType为流式输出，且字符编码为UTF-8

        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        // 禁用缓存
        response.setHeader("Cache-Control", "no-cache");
        OpenAiUtils.createStreamChatCompletion(content, response.getOutputStream());
    }

    /**
     * 生成图片
     *
     * @param prompt 图片描述
     */
    @PostMapping("/createImage")
    public void createImage(String prompt) {
        System.out.println(OpenAiUtils.createImage(prompt));
    }

    /**
     * 下载图片
     */
    @GetMapping("/downloadImage")
    public void downloadImage(String prompt, HttpServletResponse response) {
        OpenAiUtils.downloadImage(prompt, response);
    }

    @PostMapping("/billing")
    public void billing() {
        String monthUsage = OpenAiUtils.billingUsage("2023-04-01", "2023-05-01");
        System.out.println("四月使用：" + monthUsage + "美元");
        String totalUsage = OpenAiUtils.billingUsage();
        System.out.println("一共使用：" + totalUsage + "美元");
        String stageUsage = OpenAiUtils.billingUsage("2023-01-31");
        System.out.println("自从2023/01/31使用：" + stageUsage + "美元");
        Subscription subscription = OpenAiUtils.subscription();
        System.out.println("订阅信息（包含到期日期，账户总额度等信息）：" + subscription);
        // dueDate为到期日，total为总额度，usage为使用量，balance为余额
        Billing totalBilling = OpenAiUtils.billing();
        System.out.println("历史账单信息：" + totalBilling);
        // 默认不传参的billing方法的使用量usage从2023-01-01开始，如果用户的账单使用早于该日期，可以传入开始日期startDate
        Billing posibleStartBilling = OpenAiUtils.billing("2022-01-01");
        System.out.println("可能的历史账单信息：" + posibleStartBilling);
    }

    /**
     * 自定义Token使用（解决单个SpringBoot项目中只能指定唯一的Token[sk-xxxxxxxxxxxxx]的问题，现在可以自定义ChatGPTProperties内容，添加更多的Token实例）
     */
    @PostMapping("/customToken")
    public void customToken() {
        ChatGPTProperties properties = ChatGPTProperties.builder().token("sk-002xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                .proxyHost(serverIp)
                .proxyHost(proxyPort)
                .build();
        OpenAiProxyService openAiProxyService = new OpenAiProxyService(properties);
        // 直接使用new出来的openAiProxyService来调用方法，每个OpenAiProxyService都拥有自己的Token。
        // 这样在一个SpringBoot项目中，就可以有多个Token，可以有更多的免费额度供使用了
        openAiProxyService.createStreamChatCompletion("Java的三大特性是什么");
    }

    @PostMapping("/models")
    public void models() {
        System.out.println("models列表：" + OpenAiUtils.listModels());
        System.out.println("=============================================");
        System.out.println("text-davinci-003信息：" + OpenAiUtils.getModel("text-davinci-003"));
    }

    /**
     * 编辑
     */
    @PostMapping("/edit")
    public void edit() {
        String input = "What day of the wek is it?";
        String instruction = "Fix the spelling mistakes";
        System.out.println("编辑前：" + input);
        // 下面这句和OpenAiUtils.edit(input, instruction, EditModelEnum.TEXT_DAVINCI_EDIT_001);是一样的，默认使用模型TEXT_DAVINCI_EDIT_001
        System.out.println("编辑后：" + OpenAiUtils.edit(input, instruction));
        System.out.println("=============================================");
        input = "    public static void mian(String[] args) {\n" +
                "        system.in.println(\"hello world\");\n" +
                "    }";
        instruction = "Fix the code mistakes";
        System.out.println("修正代码前：\n" + input);
        System.out.println("修正代码后：\n" + OpenAiUtils.edit(input, instruction, EditModelEnum.CODE_DAVINCI_EDIT_001));
    }

    @PostMapping("/embeddings")
    public void embeddings() {
        String text = "Once upon a time";
        System.out.println("文本：" + text);
        System.out.println("文本的嵌入向量：" + OpenAiUtils.embeddings(text));
        System.out.println("=============================================");
        String[] texts = {"Once upon a time", "There was a princess"};
        System.out.println("文本数组：" + Arrays.toString(texts));
        EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
                .model(EmbeddingModelEnum.TEXT_EMBEDDING_ADA_002.getModelName()).input(Arrays.asList(texts)).build();
        System.out.println("文本数组的嵌入向量：" + OpenAiUtils.embeddings(embeddingRequest));
    }

    @PostMapping("/transcription")
    public void transcription() {
        String filePath = "src/main/resources/audio/想象之中-许嵩.mp3";
        System.out.println("语音文件转录后的text文本是：" + OpenAiUtils.transcription(filePath, AudioResponseFormatEnum.TEXT));
        // File file = new File("src/main/resources/audio/想象之中-许嵩.mp3");
        // System.out.println("语音文件转录后的text文本是：" + OpenAiUtils.transcription(file, AudioResponseFormatEnum.TEXT));
    }

    @PostMapping("/translation")
    public void translation() {
        String filePath = "src/main/resources/audio/想象之中-许嵩.mp3";
        System.out.println("语音文件翻译成英文后的text文本是：" + OpenAiUtils.translation(filePath, AudioResponseFormatEnum.TEXT));
        // File file = new File("src/main/resources/audio/想象之中-许嵩.mp3");
        // System.out.println("语音文件翻译成英文后的text文本是：" + OpenAiUtils.translation(file, AudioResponseFormatEnum.TEXT));
    }

    @PostMapping("/createImageEdit")
    public void createImageEdit() {
        CreateImageEditRequest createImageEditRequest = CreateImageEditRequest.builder().prompt("Background changed to white")
                .n(1).size(ImageSizeEnum.S512x512.getSize()).responseFormat(ImageResponseFormatEnum.URL.getResponseFormat()).build();
        ImageResult imageEdit = OpenAiUtils.createImageEdit(createImageEditRequest, "src/main/resources/image/img.png", "src/main/resources/image/mask.png");
        System.out.println("图片编辑结果：" + imageEdit);
    }

    @PostMapping("/createImageVariation")
    public void createImageVariation() {
        CreateImageVariationRequest createImageVariationRequest = CreateImageVariationRequest.builder()
                .n(2).size(ImageSizeEnum.S512x512.getSize()).responseFormat(ImageResponseFormatEnum.URL.getResponseFormat()).build();
        ImageResult imageVariation = OpenAiUtils.createImageVariation(createImageVariationRequest, "src/main/resources/image/img.png");
        System.out.println("图片变体结果：" + imageVariation);
    }

    /**
     * 文件操作（下面文件操作入参，用户可根据实际情况自行补全）
     */
    @PostMapping("/files")
    public void files() {
        // 上传文件
        System.out.println("上传文件信息：" + OpenAiUtils.uploadFile("", ""));
        // 获取文件列表
        System.out.println("文件列表：" + OpenAiUtils.listFiles());
        // 获取文件信息
        System.out.println("文件信息：" + OpenAiUtils.retrieveFile(""));
        // 获取文件内容
        System.out.println("文件内容：" + OpenAiUtils.retrieveFileContent(""));
        // 删除文件
        System.out.println("删除文件信息：" + OpenAiUtils.deleteFile(""));
    }

    @PostMapping("/fileTune")
    public void fileTune() {
        // 创建微调
        FineTuneRequest fineTuneRequest = FineTuneRequest.builder().trainingFile("").build();
        System.out.println("创建微调信息：" + OpenAiUtils.createFineTune(fineTuneRequest));
        // 创建微调完成
        CompletionRequest completionRequest = CompletionRequest.builder().build();
        System.out.println("创建微调完成信息：" + OpenAiUtils.createFineTuneCompletion(completionRequest));
        // 获取微调列表
        System.out.println("获取微调列表：" + OpenAiUtils.listFineTunes());
        // 获取微调信息
        System.out.println("获取微调信息：" + OpenAiUtils.retrieveFineTune(""));
        // 取消微调
        System.out.println("取消微调信息：" + OpenAiUtils.cancelFineTune(""));
        // 列出微调事件
        System.out.println("列出微调事件：" + OpenAiUtils.listFineTuneEvents(""));
        // 删除微调
        System.out.println("删除微调信s息：" + OpenAiUtils.deleteFineTune(""));
    }

    @PostMapping("/moderation")
    public void moderation() {
        // 创建moderation
        ModerationRequest moderationRequest = ModerationRequest.builder().input("I want to kill them.").build();
        System.out.println("创建moderation信息：" + OpenAiUtils.createModeration(moderationRequest));
    }
}
