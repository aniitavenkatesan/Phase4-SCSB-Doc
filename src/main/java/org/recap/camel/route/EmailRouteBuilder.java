package org.recap.camel.route;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.io.FileUtils;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.recap.ScsbConstants;
import org.recap.model.camel.EmailPayLoad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Created by rajeshbabuk on 19/1/17.
 */
@Component
public class EmailRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(EmailRouteBuilder.class);

    private String emailBodyForCgdUpdate;
    private String emailBodyForBatchJob;
    private String emailPassword;

    /**
     * This method instantiates a new route builder to send email.
     *
     * @param context           the context
     * @param username          the username
     * @param passwordDirectory the password directory
     * @param from              the from
     * @param upadteCgdTo       the update cgd to
     * @param batchJobTo        the batch job to
     * @param updateCgdSubject  the update cgd subject
     * @param batchJobSubject   the batch job subject
     * @param smtpServer        the smtp server
     */
    @Autowired
    public EmailRouteBuilder(CamelContext context, @Value("${" + PropertyKeyConstants.EMAIL_SMTP_SERVER_USERNAME + "}") String username, @Value("${" + PropertyKeyConstants.EMAIL_SMTP_SERVER_PASSWORD_FILE + "}") String passwordDirectory,
                             @Value("${" + PropertyKeyConstants.EMAIL_SMTP_SERVER_ADDRESS_FROM + "}") String from, @Value("${" + PropertyKeyConstants.EMAIL_SCSB_UPDATECGD_TO + "}") String upadteCgdTo, @Value("${" + PropertyKeyConstants.EMAIL_SCSB_UPDATECGD_CC + "}") String updateCGDCC, @Value("${" + PropertyKeyConstants.EMAIL_SCSB_BATCH_JOB_TO + "}") String batchJobTo,
                             @Value("${" + PropertyKeyConstants.EMAIL_SCSB_UPDATECGD_SUBJECT + "}") String updateCgdSubject, @Value("${" + PropertyKeyConstants.EMAIL_SCSB_BATCH_JOB_SUBJECT + "}") String batchJobSubject, @Value("${" + PropertyKeyConstants.EMAIL_SMTP_SERVER + "}") String smtpServer) {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    loadEmailBodyForCgdUpdateTemplate();
                    loadEmailBodyForBatchJobTemplate();
                    loadEmailPassword();

                    from(ScsbConstants.EMAIL_Q)
                            .routeId(ScsbConstants.EMAIL_ROUTE_ID)
                            .setHeader("emailPayLoad").body(EmailPayLoad.class)
                            .onCompletion().log("Email has been sent successfully.")
                            .end()
                                .choice()
                                    .when(header(ScsbConstants.EMAIL_FOR).isEqualTo(ScsbConstants.UPDATECGD))
                                        .setHeader(ScsbConstants.SUBJECT, simple(updateCgdSubject))
                                        .setBody(simple(emailBodyForCgdUpdate))
                                        .setHeader("from", simple(from))
                                        .setHeader("to", simple(upadteCgdTo))
                                        .setHeader("cc",simple(updateCGDCC))
                                        .log("Email for update cgd")
                                        .to(ScsbConstants.SMTPS_PREFIX + smtpServer + ScsbConstants.SMTPS_USERNAME + username + ScsbConstants.SMTPS_PASSWORD + emailPassword)
                                    .when(header(ScsbConstants.EMAIL_FOR).isEqualTo(ScsbConstants.BATCHJOB))
                                        .setHeader(ScsbConstants.SUBJECT, simple(batchJobSubject + " - " + "${header.emailPayLoad.jobName}" + " - " + "${header.emailPayLoad.status}"))
                                        .setBody(simple(emailBodyForBatchJob))
                                        .setHeader("from", simple(from))
                                        .setHeader("to", simple(batchJobTo))
                                        .log("Email for batch job")
                                        .to(ScsbConstants.SMTPS_PREFIX + smtpServer + ScsbConstants.SMTPS_USERNAME + username + ScsbConstants.SMTPS_PASSWORD + emailPassword)
                                    .when(header(ScsbConstants.EMAIL_FOR).isEqualTo(ScsbConstants.MATCHING_REPORTS))
                                        .setHeader(ScsbConstants.SUBJECT, simple(ScsbConstants.MATCHING_ALGORITHM_REPORTS))
                                        .setBody(simple(ScsbConstants.EMAIL_HEADER_MESSAGE))
                                        .setHeader("from", simple(from))
                                        .setHeader("to", simple(ScsbConstants.EMAIL_HEADER_TO))
                                        .setHeader("cc", simple(ScsbConstants.EMAIL_HEADER_CC))
                                        .log("Email For Matching algorithm reports")
                                        .to(ScsbConstants.SMTPS_PREFIX + smtpServer + ScsbConstants.SMTPS_USERNAME + username + ScsbConstants.SMTPS_PASSWORD + emailPassword)
                                    .when(header(ScsbConstants.EMAIL_FOR).isEqualTo(ScsbConstants.ACCESSION_REPORTS))
                                        .setHeader(ScsbConstants.SUBJECT, simple(ScsbConstants.ACCESSION_BATCH_COMPLETE))
                                        .setBody(simple(ScsbConstants.EMAIL_HEADER_MESSAGE))
                                        .setHeader("from", simple(from))
                                        .setHeader("to", simple(ScsbConstants.EMAIL_HEADER_TO))
                                        .setHeader("cc", simple(ScsbConstants.EMAIL_HEADER_CC))
                                        .log("Email For Accession reports")
                                        .to(ScsbConstants.SMTPS_PREFIX + smtpServer + ScsbConstants.SMTPS_USERNAME + username + ScsbConstants.SMTPS_PASSWORD + emailPassword)
                                    .when(header(ScsbConstants.EMAIL_FOR).isEqualTo(ScsbConstants.ACCESSION_JOB_FAILURE))
                                        .setHeader(ScsbConstants.SUBJECT, simple(ScsbConstants.ACCESSION_JOB_FAILURE))
                                        .setBody(simple(ScsbConstants.EMAIL_HEADER_MESSAGE))
                                        .setHeader("from", simple(from))
                                        .setHeader("to", simple(ScsbConstants.EMAIL_HEADER_TO))
                                        .setHeader("cc", simple(ScsbConstants.EMAIL_HEADER_CC))
                                        .log("Email For Accession Job Failure")
                                        .to(ScsbConstants.SMTPS_PREFIX + smtpServer + ScsbConstants.SMTPS_USERNAME + username + ScsbConstants.SMTPS_PASSWORD + emailPassword);
                }

                private void loadEmailBodyForCgdUpdateTemplate() {
                    InputStream inputStream = getClass().getResourceAsStream("updateCgd_email_body.vm");
                    emailBodyForCgdUpdate = getEmailBodyString(inputStream).toString();
                }

                private void loadEmailBodyForBatchJobTemplate() {
                    InputStream inputStream = getClass().getResourceAsStream("batchJobEmail.vm");
                    emailBodyForBatchJob = getEmailBodyString(inputStream).toString();
                }

                private StringBuilder getEmailBodyString(InputStream inputStream) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder out = new StringBuilder();
                    String line;
                    try {
                        while ((line = reader.readLine()) != null) {
                            if (!line.isEmpty()) {
                                out.append(line);
                            }
                            out.append("\n");
                        }
                    } catch (IOException e) {
                        logger.error(ScsbCommonConstants.LOG_ERROR, e);
                    }
                    return out;
                }

                private void loadEmailPassword() {
                    File file = new File(passwordDirectory);
                    if (file.exists()) {
                        try {
                            emailPassword = FileUtils.readFileToString(file, StandardCharsets.UTF_8).trim();
                        } catch (IOException e) {
                            logger.error(ScsbCommonConstants.LOG_ERROR,e);
                        }
                    }
                }
            });
        } catch (Exception e) {
            logger.error(ScsbCommonConstants.LOG_ERROR,e);
        }
    }

}
