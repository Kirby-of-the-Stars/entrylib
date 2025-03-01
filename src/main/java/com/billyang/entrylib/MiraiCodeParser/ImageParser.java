package com.billyang.entrylib.MiraiCodeParser;

import com.billyang.entrylib.Config.UserIO;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.message.data.*;
import top.mrxiaom.overflow.OverflowAPI;

import java.io.*;
import java.net.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ImageProcessor 类
 * 图片处理器
 * 实现对图片进行转义与反转义
 * @author Bill Yang
 */
public class ImageParser {

    String path;

    /**
     * 初始化
     * @param path 提供数据路径
     */
    public void init(String path) {
        this.path = path + "/images/";

        File file = new File(this.path);
        if(!file.exists()) {
            file.mkdirs();
        }
    }

    /**
     * 构造函数
     * 自动初始化
     */
    public ImageParser(String path) {
        init(path);
    }

    /**
     * 下载一个图片
     * 根据图片本身包含的下载地址，缓存到插件目录中
     * @param img 图片文件
     * @return 下载成功或否
     * @see Image
     */
    boolean downloadImage(Image img) {
        String imageId = img.getImageId();
        String ext;

        try {
            URL url = new URL(imageId);
            URLConnection connection = url.openConnection();
            String contentType = connection.getContentType();

            Pattern pattern = Pattern.compile("^image/([^/]+)$");
            Matcher matcher = pattern.matcher(contentType);

            if (matcher.find()) {
                ext = matcher.group(1);
            } else {
                // 如果没找到 默认返回 jpg
                ext = "jpg";
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Pattern pattern = Pattern.compile("fileid=(.*?)(?=[_&])");
        Matcher matcher = pattern.matcher(imageId);
        if(matcher.find()) {
            // 将新的 url 的图片 id 提取出来
            imageId = matcher.group(1);
            File file = new File(path, imageId + "." + ext);
            if(file.exists()) {
                return true; //文件已存在，无需下载
            }

            try {
                URL url = new URL(Image.queryUrl(img));

                URLConnection conn = url.openConnection();
                InputStream inStream = conn.getInputStream();
                FileOutputStream fs = new FileOutputStream(file);

                byte[] buffer = new byte[1204];
                int byteread;

                while ((byteread = inStream.read(buffer)) != -1) fs.write(buffer, 0, byteread);

                inStream.close();
                fs.close();

                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    /**
     * 将图片 Mirai 码转化为 图片 ID
     * @param code Mirai 码
     * @return 图片 ID
     */
    public static String MiraiCode2Id(String code) {
        // return code.replace("[mirai:image:", "").replace("]","");
        Pattern urlPattern = Pattern.compile("url=(.*?)(,|])");
        Matcher m = urlPattern.matcher(code);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 将图片 Mirai 码转化为 图片 ID
     * @param code Mirai 码
     * @return 图片 ID
     */
    public static String File2Id(String code) {
        // return code.replace("[mirai:image:", "").replace("]","");
        Pattern urlPattern = Pattern.compile("file://(.*?)(?=[,\\]]|$)");
        Matcher m = urlPattern.matcher(code);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 将消息队列中的图片转义为作为 Mirai 码的字符串
     * 根据用户配置决定是否本地缓存图片
     * @param uio 用户配置
     * @param msgChain 消息队列
     * @return 转义后的消息队列
     * @see UserIO#getImageDownloadMode()
     */
    public MessageChain Image2PlainText(UserIO uio, MessageChain msgChain) {
        MessageChainBuilder builder = new MessageChainBuilder();

        boolean download = uio.getImageDownloadMode(); //查询下载选项

        for(SingleMessage msg : msgChain) {
            if(msg instanceof Image) {
                Image img = (Image) msg;
                // 如果开启了缓存
                if(download) {
                    // 先下载
                    downloadImage(img);
                    String ext;
                    try {
                        URL url = new URL(img.getImageId());
                        URLConnection connection = url.openConnection();
                        String contentType = connection.getContentType();

                        Pattern pattern = Pattern.compile("^image/([^/]+)$");
                        Matcher matcher = pattern.matcher(contentType);

                        if (matcher.find()) {
                            ext = matcher.group(1);
                        } else {
                            // 如果没找到 默认返回 jpg
                            ext = "jpg";
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    // 改用本地地址
                    Pattern pattern = Pattern.compile("fileid=(.*?)(?=[_&])");
                    Matcher matcher = pattern.matcher(img.getImageId());
                    if(matcher.find()) {
                        // 将新的 url 的图片 id 提取出来
                        String imageId = matcher.group(1);
                        File file = new File(path, imageId + "." + ext);
                        img = OverflowAPI.get().imageFromFile("file://" + file.getAbsolutePath());
                        msg = new PlainText(img.toString());
                    }
                } else {
                    msg = new PlainText("[overflow:image,url=" + img.getImageId() + "]");
                }
                // msg = new PlainText(img.serializeToMiraiCode());
            }
            builder.append(msg);
        }

        return builder.build();
    }

    /**
     * 图片 Mirai 码的正则匹配式
     * @see Image
     */
    public static String old_regex = "\\[mirai:image:\\{[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}\\}\\..{3,5}]";

    // 新正则（匹配overflow格式）
    public static String regex = "\\[overflow:image,url=([^]]+)]";
    /**
     * 将纯文本中的图片 Mirai 码反转义为图片
     * 根据情况上传图片或请求服务器
     * @param g 原消息事件
     * @param msg 纯文本
     * @return 反转义后的消息队列
     */
    public MessageChain PlainText2Image(GroupMessageEvent g, String msg) {
        MessageChainBuilder builder = new MessageChainBuilder();

        Pattern pt = Pattern.compile(regex);
        Matcher mt = pt.matcher(msg);

        int start, end = 0, lastEnd = 0;

        while(mt.find()) {
            start = mt.start();
            end = mt.end();

            if(start >= 1) builder.append(new PlainText(msg.substring(lastEnd, start)));
            // 图片链接
            String imageId = File2Id(msg.substring(start, end));
            File file = new File(imageId);


            if(file.exists()) { //存在文件
                try {
                    Image img = OverflowAPI.get().imageFromFile( "file://" + file.getAbsolutePath());
                    // Image img = Contact.uploadImage(g.getGroup(), file);
                    builder.append(img);
                } catch (Exception e) {
                    builder.append(new PlainText("[图片尺寸过大]"));
                }
            } else { //不存在，尝试查询服务器
                try {
                    builder.append("[图片已过期]");
                } catch (Exception e) {
                    builder.append(new PlainText(msg.substring(start, end)));
                }
            }

            lastEnd = end;
        }

        builder.append(new PlainText(msg.substring(end)));

        return builder.build();
    }

    /**
     * 将消息队列中的图片 Mirai 码反转义为图片
     * 根据情况上传图片或请求服务器
     * @param g 原消息事件
     * @param msgChain 消息队列
     * @return 反转义后的消息队列
     */
    public MessageChain PlainText2Image(GroupMessageEvent g, MessageChain msgChain) {
        MessageChainBuilder builder = new MessageChainBuilder();

        for(SingleMessage message: msgChain) {
            if(message instanceof PlainText) builder.append(PlainText2Image(g, message.contentToString()));
            else builder.append(message);
        }
        return builder.build();
    }


}
