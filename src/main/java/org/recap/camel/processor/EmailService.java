package org.recap.camel.processor;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbConstants;
import org.recap.model.camel.EmailPayLoad;
import org.recap.util.CommonUtil;
import org.recap.util.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

/**
 * Created by angelind on 25/7/17.
 */
@Service
@Scope("prototype")
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private PropertyUtil propertyUtil;

    @Autowired
    private CommonUtil commonUtil;

    @Value("${" + PropertyKeyConstants.SCSB_EMAIL_ASSIST_TO + "}")
    private String recapSupportEmailTo;

    private String institutionCode;

    /**
     * Instantiates a new Email service.
     */
    public EmailService() {
        //Do Nothing
    }

    /**
     * Gets institution code.
     *
     * @return the institution code
     */
    public String getInstitutionCode() {
        return institutionCode;
    }

    /**
     * Sets institution code.
     *
     * @param institutionCode the institution code
     */
    public void setInstitutionCode(String institutionCode) {
        this.institutionCode = institutionCode;
    }

    /**
     * Send email for matching reports.
     *
     * @param exchange the exchange
     */
    public void sendEmailForMatchingReports(Exchange exchange) {
        logger.info("matching algorithm reports email started ");
        producerTemplate.sendBodyAndHeader(ScsbConstants.EMAIL_Q, getEmailPayLoadForMatching(exchange), ScsbConstants.EMAIL_FOR, ScsbConstants.MATCHING_REPORTS);
    }

    /**
     * Send email for accession reports.
     *
     * @param exchange the exchange
     */
    public void sendEmailForAccessionReports(Exchange exchange) {
        logger.info("accession reports email started ");
        producerTemplate.sendBodyAndHeader(ScsbConstants.EMAIL_Q, getEmailPayLoadForAccession(exchange), ScsbConstants.EMAIL_FOR, ScsbConstants.ACCESSION_REPORTS);
    }

    /**
     * Get email pay load for matching email pay load.
     *
     * @param exchange the exchange
     * @return the email pay load
     */
    public EmailPayLoad getEmailPayLoadForMatching(Exchange exchange){
        EmailPayLoad emailPayLoad = new EmailPayLoad();
        String fileNameWithPath = (String)exchange.getIn().getHeader("CamelAwsS3Key");
        File file = FileUtils.getFile(fileNameWithPath);
        String path = file.getParent();
        emailPayLoad.setTo(recapSupportEmailTo);
        getCc(emailPayLoad);
        emailPayLoad.setMessage("The Reports for Matching Algorithm is available at the s3 location " + path);
        logger.info("Matching Algorithm Reports email has been sent to : {} and cc : {} ",emailPayLoad.getTo(),emailPayLoad.getCc());
        return emailPayLoad;
    }

    /**
     * Get email pay load for accession email pay load.
     *
     * @param exchange the exchange
     * @return the email pay load
     */
    public EmailPayLoad getEmailPayLoadForAccession(Exchange exchange){
        EmailPayLoad emailPayLoad = new EmailPayLoad();
       String fileNameWithPath = (String)exchange.getIn().getHeader("CamelAwsS3Key");
        institutionCode = (String) exchange.getIn().getHeader(ScsbConstants.INSTITUTION_NAME);
        File file = FileUtils.getFile(fileNameWithPath);
        String absolutePath = file.getParent();
        String fileName = file.getName();
        emailPayLoad.setTo(recapSupportEmailTo);
        getCcBasedOnInstitution(emailPayLoad);
        emailPayLoad.setMessage("The Report " + fileName + " is available at the s3 location " + absolutePath);
        logger.info("Accession Reports email has been sent to : {} and cc : {} ",emailPayLoad.getTo(),emailPayLoad.getCc());
        return emailPayLoad;
    }

    public void getCc(EmailPayLoad emailPayLoad) {
        StringBuilder cc = new StringBuilder();
        List<String> institutionCodes = commonUtil.findAllInstitutionCodesExceptSupportInstitution();
        String matchingEmailTo="";
        for (String institution : institutionCodes) {
             matchingEmailTo = propertyUtil.getPropertyByInstitutionAndKey(institution, PropertyKeyConstants.ILS.ILS_EMAIL_MATCHING_REPORTS_TO);
             cc.append(StringUtils.isNotBlank(matchingEmailTo) ? matchingEmailTo + "," : "");

        }
        emailPayLoad.setCc(cc.toString());
    }

    private void getCcBasedOnInstitution(EmailPayLoad emailPayLoad) {
        emailPayLoad.setCc(propertyUtil.getPropertyByInstitutionAndKey(institutionCode, PropertyKeyConstants.ILS.ILS_EMAIL_MATCHING_REPORTS_TO));
    }
}
