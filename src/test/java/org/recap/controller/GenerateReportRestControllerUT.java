package org.recap.controller;

import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.recap.BaseTestCaseUT;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbConstants;
import org.recap.TestUtil;
import org.recap.model.jpa.InstitutionEntity;
import org.recap.model.jpa.JobParamDataEntity;
import org.recap.model.jpa.JobParamEntity;
import org.recap.model.solr.SolrIndexRequest;
import org.recap.report.ReportGenerator;
import org.recap.repository.jpa.InstitutionDetailsRepository;
import org.recap.repository.jpa.JobParamDetailRepository;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by Anitha on 10/10/20.
 */

public class GenerateReportRestControllerUT extends BaseTestCaseUT {

    @InjectMocks
    GenerateReportRestController generateReportRestController;

    @Mock
    JobParamDetailRepository jobParamDetailRepository;

    @Mock
    InstitutionDetailsRepository institutionDetailsRepository;

    @Mock
    ReportGenerator reportGenerator;

    @Value("${" + PropertyKeyConstants.SCSB_SUPPORT_INSTITUTION + "}")
    private String supportInstitution;

    @Test
    public void generateReportsJob() throws Exception{
        Mockito.when(jobParamDetailRepository.findByJobName(Mockito.anyString())).thenReturn(getJobParamEntity());
        Mockito.when(institutionDetailsRepository.findByInstitutionCodeNotIn(Mockito.anyList())).thenReturn(getInstitutionEntities());
        Mockito.when(reportGenerator.generateReport(Mockito.anyString(),Mockito.anyString(),Mockito.anyString(),Mockito.anyString(),Mockito.any(),Mockito.any())).thenReturn(ScsbConstants.SUBMIT_COLLECTION_SUMMARY_REPORT);
        String reponse =generateReportRestController.generateReportsJob(getSolrIndexRequest());
        assertEquals("Report generated Successfully in S3",reponse);
    }

    private SolrIndexRequest getSolrIndexRequest() {
        SolrIndexRequest solrIndexRequest = new SolrIndexRequest();
        solrIndexRequest.setProcessType("test");
        return solrIndexRequest;
    }

    private List<InstitutionEntity> getInstitutionEntities() {
        List<InstitutionEntity> institutionEntities = new ArrayList<>();
        institutionEntities.add(TestUtil.getInstitutionEntity(4, supportInstitution, supportInstitution));
        institutionEntities.add(TestUtil.getInstitutionEntity(2, "CUL", "CUL"));
        return institutionEntities;
    }

    @Test
    public void generateReportsJobFail() throws Exception{
        Mockito.when(jobParamDetailRepository.findByJobName(Mockito.anyString())).thenReturn(getJobParamEntity());
        Mockito.when(institutionDetailsRepository.findByInstitutionCodeNotIn(Mockito.anyList())).thenReturn(getInstitutionEntities());
        Mockito.when(reportGenerator.generateReport(Mockito.anyString(),Mockito.anyString(),Mockito.anyString(),Mockito.anyString(),Mockito.any(),Mockito.any())).thenReturn("");
        String reponse =generateReportRestController.generateReportsJob(getSolrIndexRequest());
        assertEquals("There is no report to generate or Report Generation Failed",reponse);
    }

    private JobParamEntity getJobParamEntity() {
        JobParamEntity jobParamEntity = new JobParamEntity();
        List<JobParamDataEntity> jobParamDataEntities = new ArrayList<>();
        jobParamDataEntities.add(getJobParamDataEntity(ScsbConstants.TRANSMISSION_TYPE));
        jobParamDataEntities.add(getJobParamDataEntity(ScsbConstants.REPORT_TYPE));
        jobParamDataEntities.add(getJobParamDataEntity(ScsbConstants.JOB_PARAM_DATA_FILE_NAME));
        jobParamEntity.setJobParamDataEntities(jobParamDataEntities);
        return jobParamEntity;
    }

    @Test
    public void generateSubmitCollectionReport() throws Exception{
        List<Integer> reportRecordNumberList= Arrays.asList(1,2,3);
        Mockito.when(reportGenerator.generateReportBasedOnReportRecordNum(Mockito.anyList(),Mockito.anyString(),Mockito.anyString())).thenReturn("test");
        String reponse =generateReportRestController.generateSubmitCollectionReport(reportRecordNumberList);
        assertEquals("test",reponse);
    }

    private JobParamDataEntity getJobParamDataEntity(String name) {
        JobParamDataEntity jobParamDataEntity=new JobParamDataEntity();
        jobParamDataEntity.setParamName(name);
        jobParamDataEntity.setParamValue("1");
        return jobParamDataEntity;
    }

}
