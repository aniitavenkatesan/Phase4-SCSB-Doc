package org.recap;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jms.activemq.ActiveMQAutoConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.data.solr.repository.config.EnableSolrRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.File;

/**
 * The Main class is used to lanuch the spring boot application.
 */
@SpringBootApplication(exclude = ActiveMQAutoConfiguration.class)
@EnableTransactionManagement
@EnableJpaRepositories(value = "org.recap.repository.jpa")
@EnableSolrRepositories(value = "org.recap.repository.solr.main")
@PropertySource("classpath:application.properties")
public class Main {

    /**
     * The Solr server protocol.
     */
	@Value("${" + PropertyKeyConstants.SOLR_SERVER_PROTOCOL + "}")
	String solrServerProtocol;

    /**
     * The Solr url.
     */
	@Value("${" + PropertyKeyConstants.SOLR_URL + "}")
	String solrUrl;

    /**
     * The Solr parent core.
     */
	@Value("${" + PropertyKeyConstants.SOLR_PARENT_CORE + "}")
	String solrParentCore;

    /**
     * The Tomcat max parameter count.
     */
	@Value("${" + PropertyKeyConstants.TOMCAT_MAXPARAMETERCOUNT + "}")
	Integer tomcatMaxParameterCount;

    /**
     * Solr admin client.
     *
     * @return the solr client
     */
    @Bean
	public SolrClient solrAdminClient() {
		return new HttpSolrClient.Builder(solrServerProtocol + solrUrl).build();
	}

    /**
     * Instantiates http solr client.
     *
     * @return the solr client
     */
    @Bean
	public SolrClient solrClient() {
		String baseURLForParentCore = solrServerProtocol + solrUrl + File.separator;
		return new HttpSolrClient.Builder(baseURLForParentCore).build();
	}

    /**
     * Instantiates solr template.
     *
     * @param solrClient the solr client
     * @return the solr template
     * @throws Exception the exception
     */
	@Bean
	public SolrTemplate recapSolrTemplate(SolrClient solrClient) throws Exception {
		String baseURLForParentCore = solrServerProtocol + solrUrl + File.separator + solrParentCore;
		return new SolrTemplate(new HttpSolrClient.Builder(baseURLForParentCore).build());
	}

    /**
     * Gets tomcat embedded servlet container factory.
     *
     * @return the embedded servlet container factory
     */
	@Bean
	public TomcatServletWebServerFactory servletContainerFactory() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		factory.addConnectorCustomizers( connector -> connector.setMaxParameterCount(tomcatMaxParameterCount));
		return factory;
	}

    /**
     * The entry point of application.
     *
     * @param args the input arguments
     */
    public static void main(String[] args) {
		SpringApplication.run(Main.class, args);
	}
}
