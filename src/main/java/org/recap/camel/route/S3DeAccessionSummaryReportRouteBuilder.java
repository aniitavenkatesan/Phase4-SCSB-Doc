package org.recap.camel.route;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.s3.S3Constants;
import org.apache.camel.model.dataformat.BindyType;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.recap.ScsbConstants;
import org.recap.model.csv.DeAccessionSummaryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Created by chenchulakshmig on 13/10/16.
 */
@Component
public class S3DeAccessionSummaryReportRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(S3DeAccessionSummaryReportRouteBuilder.class);

    /**
     * This method instantiates a new route builder to generate deaccession summary report file to the s3.
     *
     * @param context        the context
     * @param deaccessionPathS3 the deaccession Reports Path
     */
    @Autowired
    public S3DeAccessionSummaryReportRouteBuilder(CamelContext context, @Value("${" + PropertyKeyConstants.S3_ADD_S3_ROUTES_ON_STARTUP + "}") boolean addS3RoutesOnStartup, @Value("${" + PropertyKeyConstants.S3_DEACCESSION_COLLECTION_REPORT_DIR + "}") String deaccessionPathS3) {
        try {
            if (addS3RoutesOnStartup) {
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from(ScsbConstants.FTP_DE_ACCESSION_SUMMARY_REPORT_Q)
                                .routeId(ScsbConstants.FTP_DE_ACCESSION_SUMMARY_REPORT_ID)
                                .marshal().bindy(BindyType.Csv, DeAccessionSummaryRecord.class)
                                .setHeader(S3Constants.KEY, simple(deaccessionPathS3 + "${in.header.fileName}-${date:now:ddMMMyyyyHHmmss}.csv"))
                                .to(ScsbConstants.SCSB_CAMEL_S3_TO_ENDPOINT);
                    }
                });
            }
        } catch (Exception e) {
            logger.error(ScsbCommonConstants.LOG_ERROR, e);
        }
    }
}
