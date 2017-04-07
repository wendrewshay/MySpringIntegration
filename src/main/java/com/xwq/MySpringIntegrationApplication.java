package com.xwq;

import java.io.File;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.channel.MessageChannels;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.dsl.file.Files;
import org.springframework.integration.dsl.mail.Mail;
import org.springframework.integration.feed.inbound.FeedEntryMessageSource;
import org.springframework.integration.file.support.FileExistsMode;
import org.springframework.integration.scheduling.PollerMetadata;

import com.rometools.rome.feed.synd.SyndEntry;

@SpringBootApplication
public class MySpringIntegrationApplication {

	@Value("https://spring.io/blog.atom")
	Resource resource;
	
	/*//
	 * ******************************读取流程开始***********************************/
	
	@Bean(name=PollerMetadata.DEFAULT_POLLER)
	public PollerMetadata poller() {
		//构造feed的入站通道适配器作为数据输入
		return Pollers.fixedRate(500).get();
	}
	
	@Bean
	public FeedEntryMessageSource feedMessageSource() throws IOException {
		//构造feed的入站通道适配器作为数据输入
		FeedEntryMessageSource messageSource = new FeedEntryMessageSource(resource.getURL(), "news");
		return messageSource;
	}
	
	@Bean
	public IntegrationFlow myFlow() throws IOException {
		return IntegrationFlows.from(feedMessageSource())//流程从from方法开始
				//选择路由，消息体类型为SyndEntry，作为判断条件的类型为String
				.<SyndEntry, String> route( 
						//判断的值是通过payload获得的分类category
						payload -> payload
							.getCategories().get(0).getName(),
						//通过不同分类的值转向不同的消息通道
						mapping -> mapping
							.channelMapping("releases", "releasesChannel")
							.channelMapping("engineering", "engineeringChannel")
							.channelMapping("news", "newsChannel"))
				.get();
	}
	
	/*//
	 * ******************************读取流程结束***********************************/
	/*//
	 * ******************************release流程开始***********************************/
	@Bean
	public IntegrationFlow releasesFlow() {
				//从消息通道releasesChannel开始获取数据
		return IntegrationFlows.from(MessageChannels.queue("releasesChannel", 10))
				//使用transform方法进行数据转换，payload类型为syndEntry，
				//将其转换为字符串类型并自定义数据的格式
				.<SyndEntry, String> transform(payload -> " 《" +payload.getTitle() + "》 "
						+ payload.getLink() + "\r\n")
				//用handle方法处理file的出站适配器。Files类是由Spring Integration Java DSL提供的
				//Fluent API用来构造文件输出的适配器
				.handle(Files.outboundAdapter(new File("D:/springlog"))
						.fileExistsMode(FileExistsMode.APPEND)
						.charset("UTF-8")
						.fileNameGenerator(message -> "releases.txt")
						.get())
				.get();
					
	}
	/*//
	 * ******************************release流程结束***********************************/
	/*//
	 * ******************************engineering流程开始***********************************/
	@Bean
	public IntegrationFlow engineeringFlow() {
		return IntegrationFlows.from(MessageChannels.queue("engineeringChannel", 10))
				.<SyndEntry, String> transform(e -> " 《" + e.getTitle() + "》 " + e.getLink() + "\r\n")
				.handle(Files.outboundAdapter(new File("D:/springlog"))
						.fileExistsMode(FileExistsMode.APPEND)
						.charset("UTF-8")
						.fileNameGenerator(message -> "engineering.txt")
						.get())
				.get();
	}
	/*//
	 * ******************************engineering流程结束***********************************/
	/*//
	 * ******************************news流程开始***********************************/
	@Bean
	public IntegrationFlow newsFlow() {
		return IntegrationFlows.from(MessageChannels.queue("newsChannel", 10))
				.<SyndEntry, String> transform(payload -> " 《" +payload.getTitle() + "》 "
						+ payload.getLink() + ",")
				//通过enricherHeader来增加消息头的信息
				.enrichHeaders(
						//邮件发送的相关信息通过Spring Integration Java DSL提供的Mail的headers方法来构造
							Mail.headers()
							.subject("来自spring的新闻")//邮件标题
							.to("1194660186@qq.com")//收件人
							.from("wendrewshay@126.com"))//发件人
				//使用handle方法来定义邮件发送的出站适配器，使用Spring Integration Java DSL
				//提供的Mail.outboundAdapter来构造，这里使用自己的邮箱向自己发邮件
				.handle(Mail.outboundAdapter("smtp.126.com")
						.port(25)
						.protocol("smtp")
						.credentials("wendrewshay@126.com", "******")//账户名和密码
						.javaMailProperties(p -> p.put("mail.debug", "false")), e -> e.id("smtpOut"))
				.get();
	}
	/*//
	 * ******************************news流程结束***********************************/
	
	public static void main(String[] args) {
		SpringApplication.run(MySpringIntegrationApplication.class, args);
	}
	
}
