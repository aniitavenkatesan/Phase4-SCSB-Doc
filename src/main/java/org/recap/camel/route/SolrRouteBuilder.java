package org.recap.camel.route;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.solr.SolrConstants;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Created by rajeshbabuk on 30/8/16.
 */
@Component
public class SolrRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SolrRouteBuilder.class);

    /**
     * This method instantiates a new solr route builder to index in solr.
     *
     * @param camelContext the camel context
     * @param solrUri      the solr uri
     * @param solrCore     the solr core
     */
    @Autowired
    public SolrRouteBuilder(CamelContext camelContext,
                            @Value("${" + PropertyKeyConstants.SOLR_URL + "}") String solrUri,
                            @Value("${" + PropertyKeyConstants.SOLR_PARENT_CORE + "}") String solrCore) {

        try {
            camelContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from(ScsbCommonConstants.SOLR_QUEUE).setHeader(SolrConstants.OPERATION, constant(SolrConstants.OPERATION_INSERT))
                            .setHeader(SolrConstants.FIELD + "id", body())
                            .to("solr:" + solrUri + "/" + solrCore);
                }
            });
        } catch (Exception e) {
            logger.error(ScsbCommonConstants.LOG_ERROR,e);
        }
    }
}
