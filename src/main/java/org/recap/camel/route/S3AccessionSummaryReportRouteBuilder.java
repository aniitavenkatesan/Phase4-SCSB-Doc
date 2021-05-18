package org.recap.camel.route;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.s3.S3Constants;
import org.apache.camel.model.dataformat.BindyType;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.recap.ScsbConstants;
import org.recap.model.csv.AccessionSummaryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Created by hemalathas on 23/11/16.
 */
@Component
public class S3AccessionSummaryReportRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(S3AccessionSummaryReportRouteBuilder.class);

    /**
     * This method instantiates a new route builder to generate accession summary report file to the s3.
     *
     * @param context        the context
     * @param accessionPathS3 the accession Reports Path
     */
    @Autowired
    public S3AccessionSummaryReportRouteBuilder(CamelContext context, @Value("${" + PropertyKeyConstants.S3_ADD_S3_ROUTES_ON_STARTUP + "}") boolean addS3RoutesOnStartup, @Value("${" + PropertyKeyConstants.S3_ONGOING_ACCESSION_COLLECTION_REPORT_DIR + "}") String accessionPathS3) {
        try {
            if (addS3RoutesOnStartup) {
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from(ScsbCommonConstants.FTP_ACCESSION_SUMMARY_REPORT_Q)
                                .routeId(ScsbCommonConstants.FTP_ACCESSION_SUMMARY_REPORT_ID)
                                .marshal().bindy(BindyType.Csv, AccessionSummaryRecord.class)
                                .setHeader(S3Constants.KEY, simple(accessionPathS3 + "${in.header.fileName}-${date:now:ddMMMyyyyHHmmss}.csv"))
                                .to(ScsbConstants.SCSB_CAMEL_S3_TO_ENDPOINT);
                    }
                });
            }
        } catch (Exception e) {
            logger.error(ScsbCommonConstants.LOG_ERROR, e);
        }
    }
}
