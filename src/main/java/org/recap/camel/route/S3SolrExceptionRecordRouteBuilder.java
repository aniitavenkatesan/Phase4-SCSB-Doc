package org.recap.camel.route;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.s3.S3Constants;
import org.apache.camel.model.dataformat.BindyType;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.recap.ScsbConstants;
import org.recap.model.csv.SolrExceptionReportCSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Created by angelind on 30/9/16.
 */
@Component
public class S3SolrExceptionRecordRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(S3SolrExceptionRecordRouteBuilder.class);

    /**
     * This method instantiates a new route builder to generate solr exception record to S3.
     *
     * @param context         the context
     * @param solrReportsS3Path the s3 solrReportsPath
     */
    @Autowired
    public S3SolrExceptionRecordRouteBuilder(CamelContext context, @Value("${" + PropertyKeyConstants.S3_ADD_S3_ROUTES_ON_STARTUP + "}") boolean addS3RoutesOnStartup, @Value("${" + PropertyKeyConstants.S3_SOLR_REPORTS_DIR + "}") String solrReportsS3Path) {
        try {
            if (addS3RoutesOnStartup) {
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from(ScsbCommonConstants.FTP_SOLR_EXCEPTION_REPORT_Q)
                                .routeId(ScsbCommonConstants.FTP_SOLR_EXCEPTION_REPORT_ROUTE_ID)
                                .marshal().bindy(BindyType.Csv, SolrExceptionReportCSVRecord.class)
                                .setHeader(S3Constants.KEY, simple(solrReportsS3Path + "${in.header.fileName}-${date:now:ddMMMyyyyHHmmss}.csv"))
                                .to(ScsbConstants.SCSB_CAMEL_S3_TO_ENDPOINT)
                                .onCompletion().log("File has been uploaded to S3 successfully.");
                    }
                });
            }
        } catch (Exception e) {
            logger.error(ScsbCommonConstants.LOG_ERROR, e);
        }
    }
}
