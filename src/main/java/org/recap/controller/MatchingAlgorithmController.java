package org.recap.controller;

import org.apache.commons.lang3.StringUtils;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.recap.ScsbConstants;
import org.recap.executors.MatchingBibItemIndexExecutorService;
import org.recap.matchingalgorithm.MatchingCounter;
import org.recap.matchingalgorithm.service.MatchingAlgorithmHelperService;
import org.recap.matchingalgorithm.service.MatchingAlgorithmUpdateCGDService;
import org.recap.matchingalgorithm.service.MatchingBibInfoDetailService;
import org.recap.report.ReportGenerator;
import org.recap.repository.jpa.InstitutionDetailsRepository;
import org.recap.util.CommonUtil;
import org.recap.util.StopWatchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.validation.Valid;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.recap.ScsbConstants.MATCHING_COUNTER_UPDATED_SHARED;

/**
 * Created by angelind on 12/7/16.
 */
@Controller
public class MatchingAlgorithmController {

    private static final Logger logger = LoggerFactory.getLogger(MatchingAlgorithmController.class);

    @Autowired
    private MatchingAlgorithmHelperService matchingAlgorithmHelperService;

    @Autowired
    private ReportGenerator reportGenerator;

    @Autowired
    private MatchingAlgorithmUpdateCGDService matchingAlgorithmUpdateCGDService;

    @Autowired
    private MatchingBibInfoDetailService matchingBibInfoDetailService;

    @Value("${" + PropertyKeyConstants.MATCHING_ALGORITHM_BATCHSIZE + "}")
    private String matchingAlgoBatchSize;

    @Autowired
    private MatchingBibItemIndexExecutorService matchingBibItemIndexExecutorService;

    @Autowired
    private InstitutionDetailsRepository institutionDetailsRepository;

    @Autowired
    private CommonUtil commonUtil;

    /**
     * Gets logger.
     *
     * @return the logger
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * Gets matching algorithm helper service.
     *
     * @return the matching algorithm helper service
     */
    public MatchingAlgorithmHelperService getMatchingAlgorithmHelperService() {
        return matchingAlgorithmHelperService;
    }

    /**
     * Gets report generator.
     *
     * @return the report generator
     */
    public ReportGenerator getReportGenerator() {
        return reportGenerator;
    }

    /**
     * Sets report generator.
     *
     * @param reportGenerator the report generator
     */
    public void setReportGenerator(ReportGenerator reportGenerator) {
        this.reportGenerator = reportGenerator;
    }

    /**
     * Gets matching algorithm update cgd service.
     *
     * @return the matching algorithm update cgd service
     */
    public MatchingAlgorithmUpdateCGDService getMatchingAlgorithmUpdateCGDService() {
        return matchingAlgorithmUpdateCGDService;
    }

    /**
     * Gets matching bib info detail service.
     *
     * @return the matching bib info detail service
     */
    public MatchingBibInfoDetailService getMatchingBibInfoDetailService() {
        return matchingBibInfoDetailService;
    }

    /**
     * Gets matching algo batch size.
     *
     * @return the matching algo batch size
     */
    public String getMatchingAlgoBatchSize() {
        return matchingAlgoBatchSize;
    }

    /**
     * Gets matching bib item index executor service.
     *
     * @return the matching bib item index executor service
     */
    public MatchingBibItemIndexExecutorService getMatchingBibItemIndexExecutorService() {
        return matchingBibItemIndexExecutorService;
    }

    /**
     * The whole matching algorithm process.
     * First it finds the matching records and updates them in the database (matching_matchpoints_t)
     * Second it gets the matching records details and saves them in database
     * Then it generates reports for single match and multiple-match(based on criterias) accordingly
     * Then update cgd for Monographs in database.
     * Then update cgd for Serials in database.
     * Then update cgd for Mvms in database.
     * Then updates the cgd updated records in solr.
     *
     * @param matchingAlgoDate the matching algo date
     * @return the string
     */
    @ResponseBody
    @PostMapping(value = "/matchingAlgorithm/full")
    public String matchingAlgorithmFull(@Valid @ModelAttribute("matchingAlgoDate") String matchingAlgoDate) {
        StringBuilder status = new StringBuilder();
        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            status.append(matchingAlgorithmFindMatchingAndReports());
            status.append(updateMonographCGDInDB());
            status.append(updateSerialCGDInDB());
            status.append(updateMvmCGDInDB());
            status.append(updateCGDInSolr(matchingAlgoDate));
            stopWatch.stop();
            getLogger().info("Total Time taken to process the full Matching Algorithm Process : {}" ,  stopWatch.getTotalTimeSeconds());
            status.append(ScsbConstants.STATUS_DONE ).append("\n");
            status.append(ScsbConstants.TOTAL_TIME_TAKEN + "to run full Matching Algorithm Process : " + stopWatch.getTotalTimeSeconds()).append("\n");
        } catch (Exception e) {
            getLogger().error(ScsbCommonConstants.LOG_ERROR,e);
            status.append(ScsbConstants.STATUS_FAILED);
        }
        return status.toString();
    }

    /**
     * Matching algorithm.
     * First it finds the matching records and updates them in the database (matching_matchpoints_t)
     * Second it gets the matching records details and saves them in database
     * Then it generates reports for single match and multiple-match(based on criterias) accordingly
     *
     * @return the string
     */
    @ResponseBody
    @PostMapping(value = "/matchingAlgorithm/findMatchingAndSaveReports")
    public String matchingAlgorithmFindMatchingAndReports() {
        StringBuilder status = new StringBuilder();
        try {
            StopWatch stopWatch=new StopWatch();
            stopWatch.start();
            StopWatchUtil.executeAndEstimateTotalTimeTaken(getMatchingAlgorithmHelperService()::findMatchingAndPopulateMatchPointsEntities,"FindingAndPopulatingMatchPoints");
            StopWatchUtil.executeAndEstimateTotalTimeTaken(getMatchingAlgorithmHelperService()::populateMatchingBibEntities,"SavingMatchingBibs");
            StopWatchUtil.executeAndEstimateTotalTimeTaken(this::runReportsForMatchingAlgorithm,Integer.valueOf(getMatchingAlgoBatchSize()),"RunningReports For Initial Matching Algorithm");
            stopWatch.stop();
            getLogger().info("Total Time taken to process Matching Algorithm : {}" , stopWatch.getTotalTimeSeconds());
            status.append(ScsbConstants.STATUS_DONE ).append("\n");
            status.append(ScsbConstants.TOTAL_TIME_TAKEN + "for matching and save reports : " + stopWatch.getTotalTimeSeconds()).append("\n");
        } catch (Exception e) {
            getLogger().error(ScsbCommonConstants.LOG_ERROR,e);
            status.append(ScsbConstants.STATUS_FAILED);
        }
        return status.toString();
    }

    /**
     * This method is used for processing reports from the matching bib details .
     *
     * @return the string
     */
    @ResponseBody
    @PostMapping(value = "/matchingAlgorithm/reports")
    public String matchingAlgorithmOnlyReports() {
        StringBuilder status = new StringBuilder();
        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            runReportsForMatchingAlgorithm(Integer.valueOf(getMatchingAlgoBatchSize()));
            stopWatch.stop();
            getLogger().info("Total Time taken to process Matching Algorithm Reports :{} " , stopWatch.getTotalTimeSeconds());
            status.append(ScsbConstants.STATUS_DONE ).append("\n");
            status.append(ScsbConstants.TOTAL_TIME_TAKEN + "to save reports only : " + stopWatch.getTotalTimeSeconds()).append("\n");
        } catch (Exception e) {
            getLogger().error(ScsbCommonConstants.LOG_ERROR,e);
            status.append(ScsbConstants.STATUS_FAILED);
        }
        return status.toString();
    }

    /**
     * This method is used to update cgd for Monographs in database.
     *
     * @return the string
     */
    @ResponseBody
    @PostMapping(value = "/matchingAlgorithm/updateMonographCGDInDB")
    public String updateMonographCGDInDB() {
        StringBuilder status = new StringBuilder();
        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            getMatchingAlgorithmUpdateCGDService().updateCGDProcessForMonographs(Integer.valueOf(getMatchingAlgoBatchSize()));
            stopWatch.stop();
            getLogger().info("Total Time taken to Update Monographs CGD In DB For Matching Algorithm : {}" , stopWatch.getTotalTimeSeconds());
            status.append(ScsbConstants.STATUS_DONE ).append("\n");
            status.append(ScsbConstants.TOTAL_TIME_TAKEN + "to update monograph CGD in DB : " + stopWatch.getTotalTimeSeconds()).append("\n");
        } catch (Exception e) {
            getLogger().error(ScsbCommonConstants.LOG_ERROR,e);
            status.append(ScsbConstants.STATUS_FAILED);
        }
        return status.toString();
    }

    /**
     * This method is used to update cgd for Serials in database.
     *
     * @return the string
     */
    @ResponseBody
    @PostMapping(value = "/matchingAlgorithm/updateSerialCGDInDB")
    public String updateSerialCGDInDB() {
        StringBuilder status = new StringBuilder();
        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            getMatchingAlgorithmUpdateCGDService().updateCGDProcessForSerials(Integer.valueOf(getMatchingAlgoBatchSize()));
            stopWatch.stop();
            getLogger().info("Total Time taken to Update Serials CGD In DB For Matching Algorithm : {}" , stopWatch.getTotalTimeSeconds());
            status.append(ScsbConstants.STATUS_DONE ).append("\n");
            status.append(ScsbConstants.TOTAL_TIME_TAKEN + "to update Serial CGD in DB : " + stopWatch.getTotalTimeSeconds()).append("\n");
        } catch (Exception e) {
            getLogger().error(ScsbCommonConstants.LOG_ERROR,e);
            status.append(ScsbConstants.STATUS_FAILED);
        }
        return status.toString();
    }

    /**
     * This method is used to update cgd for MonographicSets in database.
     *
     * @return the string
     */
    @ResponseBody
    @PostMapping(value = "/matchingAlgorithm/updateMvmCGDInDB")
    public String updateMvmCGDInDB() {
        StringBuilder status = new StringBuilder();
        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            getMatchingAlgorithmUpdateCGDService().updateCGDProcessForMVMs(Integer.valueOf(getMatchingAlgoBatchSize()));
            stopWatch.stop();
            getLogger().info("Total Time taken to Update MVMs CGD In DB For Matching Algorithm : {}" , stopWatch.getTotalTimeSeconds());
            status.append(ScsbConstants.STATUS_DONE ).append("\n");
            status.append(ScsbConstants.TOTAL_TIME_TAKEN + "to update MVM CGD in DB : " + stopWatch.getTotalTimeSeconds()).append("\n");
        } catch (Exception e) {
            getLogger().error(ScsbCommonConstants.LOG_ERROR,e);
            status.append(ScsbConstants.STATUS_FAILED);
        }
        return status.toString();
    }

    /**
     * This method is used to update the cgds for the items which got updated through matching algorithm in solr.
     *
     * @param matchingAlgoDate the matching algo date
     * @return the string
     */
    @ResponseBody
    @PostMapping(value = "/matchingAlgorithm/updateCGDInSolr")
    public String updateCGDInSolr(@Valid @ModelAttribute("matchingAlgoDate") String matchingAlgoDate) {
        StringBuilder status = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
        Date updatedDate = new Date();
        if(StringUtils.isNotBlank(matchingAlgoDate)) {
            try {
                updatedDate = sdf.parse(matchingAlgoDate);
            } catch (ParseException e) {
                getLogger().error("Exception while parsing Date : {}" , e.getMessage());
            }
        }
        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            Integer totalProcessedRecords = getMatchingBibItemIndexExecutorService().indexingForMatchingAlgorithm(ScsbConstants.INITIAL_MATCHING_OPERATION_TYPE, updatedDate);
            stopWatch.stop();
            getLogger().info("Total Time taken to Update CGD In Solr For Matching Algorithm : {}" , stopWatch.getTotalTimeSeconds());
            String recordsProcessed = "Total number of records processed : " + totalProcessedRecords;
            status.append(ScsbConstants.STATUS_DONE).append("\n");
            status.append(recordsProcessed).append("\n");
            status.append(ScsbConstants.TOTAL_TIME_TAKEN + "to update CGD in solr : " + stopWatch.getTotalTimeSeconds()).append("\n");
        } catch (Exception e) {
            getLogger().error(ScsbCommonConstants.LOG_ERROR,e);
            status.append(ScsbConstants.STATUS_FAILED);
        }
        return status.toString();
    }

    /**
     * This method is used to populate matching institution bibId information for data dump.
     *
     * @return the string
     */
    @ResponseBody
    @PostMapping(value = "/matchingAlgorithm/populateDataForDataDump")
    public String populateDataForDataDump(){
        String respone  = null;
        try {
            respone = getMatchingBibInfoDetailService().populateMatchingBibInfo();
        } catch (Exception e) {
            getLogger().error(ScsbCommonConstants.LOG_ERROR,e);
        }
        return respone;
    }

    private void runReportsForMatchingAlgorithm(Integer batchSize){
        List<String> allInstitutionCodeExceptSupportInstitution = commonUtil.findAllInstitutionCodesExceptSupportInstitution();
        Map<String, Integer> institutionCounterMap = allInstitutionCodeExceptSupportInstitution.stream().collect(Collectors.toMap(Function.identity(), institution -> 0));

        getMatchingAlgorithmHelperService().populateReportsForMatchPoints(batchSize, ScsbCommonConstants.MATCH_POINT_FIELD_OCLC, ScsbCommonConstants.MATCH_POINT_FIELD_ISBN, institutionCounterMap);
        getMatchingAlgorithmHelperService().populateReportsForMatchPoints(batchSize, ScsbCommonConstants.MATCH_POINT_FIELD_OCLC, ScsbCommonConstants.MATCH_POINT_FIELD_ISSN, institutionCounterMap);
        getMatchingAlgorithmHelperService().populateReportsForMatchPoints(batchSize, ScsbCommonConstants.MATCH_POINT_FIELD_OCLC, ScsbCommonConstants.MATCH_POINT_FIELD_LCCN, institutionCounterMap);
        getMatchingAlgorithmHelperService().populateReportsForMatchPoints(batchSize, ScsbCommonConstants.MATCH_POINT_FIELD_ISBN, ScsbCommonConstants.MATCH_POINT_FIELD_ISSN, institutionCounterMap);
        getMatchingAlgorithmHelperService().populateReportsForMatchPoints(batchSize, ScsbCommonConstants.MATCH_POINT_FIELD_ISBN, ScsbCommonConstants.MATCH_POINT_FIELD_LCCN, institutionCounterMap);
        getMatchingAlgorithmHelperService().populateReportsForMatchPoints(batchSize, ScsbCommonConstants.MATCH_POINT_FIELD_ISSN, ScsbCommonConstants.MATCH_POINT_FIELD_LCCN, institutionCounterMap);
        getMatchingAlgorithmHelperService().populateReportsForSingleMatch(batchSize,institutionCounterMap);
        getMatchingAlgorithmHelperService().saveMatchingSummaryCount(institutionCounterMap);
    }

    /**
     * This method is used to count items for serials.
     *
     * @return the string
     */
// Added to produce the Summary of serial Item count which came under Matching Algorithm
    @ResponseBody
    @GetMapping(value = "/matchingAlgorithm/itemsCountForSerials")
    public String itemCountForSerials(){
        StringBuilder response = new StringBuilder();
        StopWatch stopwatch = new StopWatch();
        stopwatch.start();
        MatchingCounter.reset();
        getMatchingAlgorithmUpdateCGDService().getItemsCountForSerialsMatching(Integer.valueOf(getMatchingAlgoBatchSize()));
        List<String> allInstitutionCodesExceptSupportInstitution = commonUtil.findAllInstitutionCodesExceptSupportInstitution();
        for (String institutionCode : allInstitutionCodesExceptSupportInstitution) {
            getLogger().info("Total {} Shared Serial Items in Matching : {}" , institutionCode, MatchingCounter.getSpecificInstitutionCounterMap(institutionCode).get(MATCHING_COUNTER_UPDATED_SHARED));
            response.append(institutionCode+" Shared Serial Items Count : ").append(MatchingCounter.getSpecificInstitutionCounterMap(institutionCode).get(MATCHING_COUNTER_UPDATED_SHARED)).append("\n");
        }
        stopwatch.stop();
        getLogger().info("Total Time taken to get the serial items count : {}" , stopwatch.getTotalTimeSeconds() + " seconds");
        return response.toString();
    }

}
