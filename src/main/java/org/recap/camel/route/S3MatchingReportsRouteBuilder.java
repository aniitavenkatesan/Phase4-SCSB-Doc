package org.recap.camel.route;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.s3.S3Constants;
import org.apache.camel.model.dataformat.BindyType;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.recap.ScsbConstants;
import org.recap.camel.processor.EmailService;
import org.recap.camel.processor.StopRouteProcessor;
import org.recap.model.matchingreports.MatchingSerialAndMVMReports;
import org.recap.model.matchingreports.MatchingSummaryReport;
import org.recap.model.matchingreports.OngoingMatchingCGDReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Created by angelind on 22/6/17.
 */
@Component
public class S3MatchingReportsRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(S3MatchingReportsRouteBuilder.class);

    /**
     * Instantiates a new s3 matching reports route builder.
     *
     * @param camelContext               the camel context
     * @param matchingReportsDirectory   the matching reports directory
     * @param s3MatchingReportsDirectory the s3 MatchingReports Directory
     * @param applicationContext         the application context
     */
    public S3MatchingReportsRouteBuilder(CamelContext camelContext, @Value("${" + PropertyKeyConstants.S3_ADD_S3_ROUTES_ON_STARTUP + "}") boolean addS3RoutesOnStartup, @Value("${" + PropertyKeyConstants.ONGOING_MATCHING_REPORT_DIRECTORY + "}") String matchingReportsDirectory,
                                         @Value("${" + PropertyKeyConstants.S3_MATCHINGALGORITHM_REPORTS_DIR + "}") String s3MatchingReportsDirectory, ApplicationContext applicationContext) {
        if (addS3RoutesOnStartup) {
            try {
                camelContext.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from(ScsbConstants.FILE + matchingReportsDirectory + ScsbConstants.DELETE_FILE_OPTION)
                                .routeId(ScsbConstants.FTP_TITLE_EXCEPTION_REPORT_ROUTE_ID)
                                .noAutoStartup()
                                .setHeader(S3Constants.KEY, simple(s3MatchingReportsDirectory+ "${in.header.CamelFileName}"))
                                .to(ScsbConstants.SCSB_CAMEL_S3_TO_ENDPOINT)
                                .onCompletion()
                                .process(new StopRouteProcessor(ScsbConstants.FTP_TITLE_EXCEPTION_REPORT_ROUTE_ID))
                                .log("Title_Exception report generated and uploaded to s3 successfully.");
                    }
                });
            } catch (Exception e) {
                logger.info(ScsbCommonConstants.LOG_ERROR, e);
            }

            try {
                camelContext.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from(ScsbConstants.FTP_SERIAL_MVM_REPORT_Q)
                                .routeId(ScsbConstants.FTP_SERIAL_MVM_REPORT_ROUTE_ID)
                                .noAutoStartup()
                                .marshal().bindy(BindyType.Csv, MatchingSerialAndMVMReports.class)
                                .setHeader(S3Constants.KEY, simple(s3MatchingReportsDirectory+ ScsbConstants.MATCHING_REPORT_FILE_NAME_CAMEL_HEADER))
                                .to(ScsbConstants.SCSB_CAMEL_S3_TO_ENDPOINT)
                                .onCompletion()
                                .process(new StopRouteProcessor(ScsbConstants.FTP_SERIAL_MVM_REPORT_ROUTE_ID))
                                .log("Matching Serial_MVM reports generated and uploaded to s3 successfully.");

                    }
                });
            } catch (Exception e) {
                logger.info(ScsbCommonConstants.LOG_ERROR, e);
            }

            try {
                camelContext.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from(ScsbConstants.FTP_MATCHING_SUMMARY_REPORT_Q)
                                .routeId(ScsbConstants.FTP_MATCHING_SUMMARY_REPORT_ROUTE_ID)
                                .noAutoStartup()
                                .marshal().bindy(BindyType.Csv, MatchingSummaryReport.class)
                                .setHeader(S3Constants.KEY, simple(s3MatchingReportsDirectory+ ScsbConstants.MATCHING_REPORT_FILE_NAME_CAMEL_HEADER))
                                .to(ScsbConstants.SCSB_CAMEL_S3_TO_ENDPOINT)
                                .onCompletion()
                                .bean(applicationContext.getBean(EmailService.class), ScsbConstants.MATCHING_REPORTS_SEND_EMAIL)
                                .process(new StopRouteProcessor(ScsbConstants.FTP_MATCHING_SUMMARY_REPORT_ROUTE_ID))
                                .log("Matching Summary reports generated and uploaded to s3 successfully.")
                                .end();

                    }
                });
            } catch (Exception e) {
                logger.info(ScsbCommonConstants.LOG_ERROR, e);
            }

            try {
                camelContext.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from(ScsbConstants.S3_ONGOING_MATCHING_CGD_REPORT_Q)
                                .routeId(ScsbConstants.S3_ONGOING_MATCHING_CGD_REPORT_ROUTE_ID)
                                .noAutoStartup()
                                .marshal().bindy(BindyType.Csv, OngoingMatchingCGDReport.class)
                                .setHeader(S3Constants.KEY, simple(s3MatchingReportsDirectory+"cgd-round-trip/"+"${in.header.Institution}"+"/"+ ScsbConstants.MATCHING_REPORT_FILE_NAME_CAMEL_HEADER))
                                .to(ScsbConstants.SCSB_CAMEL_S3_TO_ENDPOINT)
                                .onCompletion()
                                .bean(applicationContext.getBean(EmailService.class), ScsbConstants.MATCHING_REPORTS_SEND_EMAIL)
                                .process(new StopRouteProcessor(ScsbConstants.S3_ONGOING_MATCHING_CGD_REPORT_ROUTE_ID))
                                .log("Ongoing Matching CGD reports generated and uploaded to s3 successfully.")
                                .end();

                    }
                });
            } catch (Exception e) {
                logger.info(ScsbCommonConstants.LOG_ERROR, e);
            }

        }
    }
}
