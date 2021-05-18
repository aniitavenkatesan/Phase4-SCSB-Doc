package org.recap.controller;

import org.apache.commons.lang3.StringUtils;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.recap.ScsbConstants;
import org.recap.model.jpa.InstitutionEntity;
import org.recap.model.jpa.JobParamDataEntity;
import org.recap.model.jpa.JobParamEntity;
import org.recap.model.solr.SolrIndexRequest;
import org.recap.report.ReportGenerator;
import org.recap.repository.jpa.InstitutionDetailsRepository;
import org.recap.repository.jpa.JobParamDetailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;


/**
 * Created by angelind on 28/4/17.
 */
@RestController
@RequestMapping("/generateReportService")
public class GenerateReportRestController {

    private static final Logger logger = LoggerFactory.getLogger(GenerateReportRestController.class);

    @Autowired
    private ReportGenerator reportGenerator;

    @Autowired
    private JobParamDetailRepository jobParamDetailRepository;

    @Autowired
    private InstitutionDetailsRepository institutionDetailsRepository;

    @Value("${" + PropertyKeyConstants.SCSB_SUPPORT_INSTITUTION + "}")
    private String supportInstitution;

    @PostMapping(value = "/generateReports", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public String generateReportsJob(@RequestBody SolrIndexRequest solrIndexRequest) {
        String status;
        Date fromDate = solrIndexRequest.getCreatedDate();
        Date toDate = new Date();
        JobParamEntity jobParamEntity = jobParamDetailRepository.findByJobName(solrIndexRequest.getProcessType());
        Map<String, String> jobParamMap = new HashMap<>();
        for(JobParamDataEntity jobParamDataEntity : jobParamEntity.getJobParamDataEntities()) {
            jobParamMap.put(jobParamDataEntity.getParamName(), jobParamDataEntity.getParamValue());
        }
        String transmissionType = jobParamMap.get(ScsbConstants.TRANSMISSION_TYPE);
        String reportType = jobParamMap.get(ScsbConstants.REPORT_TYPE);
        String fileName = jobParamMap.get(ScsbConstants.JOB_PARAM_DATA_FILE_NAME);
        StringBuilder generateReportFileName = new StringBuilder();
        Iterable<InstitutionEntity> institutionEntities = institutionDetailsRepository.findByInstitutionCodeNotIn(Arrays.asList(supportInstitution));

        for (Iterator<InstitutionEntity> iterator = institutionEntities.iterator(); iterator.hasNext(); ) {
            InstitutionEntity institutionEntity = iterator.next();
            String generatedFileName = reportGenerator.generateReport(fileName, institutionEntity.getInstitutionCode(), reportType, transmissionType, fromDate, toDate);
            if(StringUtils.isNotBlank(generatedFileName)) {
                generateReportFileName.append(generatedFileName);
                if(iterator.hasNext()) {
                    generateReportFileName.append("\n");
                }
            }
        }
        if(StringUtils.isNotBlank(generateReportFileName.toString())) {
            logger.info("Created report fileNames : {}", generateReportFileName);
            status = "Report generated Successfully in S3";
        } else {
            logger.info("No report files generated.");
            status = "There is no report to generate or Report Generation Failed";
        }

        return status;
    }

    @ResponseBody
    @PostMapping(value="/generateSubmitCollectionReport")
    public String generateSubmitCollectionReport(@RequestBody List<Integer> reportRecordNumberList) {
       return reportGenerator.generateReportBasedOnReportRecordNum(reportRecordNumberList, ScsbConstants.SUBMIT_COLLECTION, ScsbCommonConstants.FTP);
    }

}
