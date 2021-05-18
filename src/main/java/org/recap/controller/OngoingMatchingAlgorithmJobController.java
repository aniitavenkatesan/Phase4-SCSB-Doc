package org.recap.controller;

import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.recap.ScsbConstants;
import org.recap.matchingalgorithm.service.MatchingBibInfoDetailService;
import org.recap.model.solr.SolrIndexRequest;
import org.recap.util.DateUtil;
import org.recap.util.OngoingMatchingAlgorithmUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Date;

/**
 * Created by angelind on 16/3/17.
 */
@Controller
public class OngoingMatchingAlgorithmJobController {

    private static final Logger logger = LoggerFactory.getLogger(OngoingMatchingAlgorithmJobController.class);

    @Autowired
    private OngoingMatchingAlgorithmUtil ongoingMatchingAlgorithmUtil;

    @Autowired
    private MatchingBibInfoDetailService matchingBibInfoDetailService;

    @Autowired
    private DateUtil dateUtil;

    @Value("${" + PropertyKeyConstants.MATCHING_ALGORITHM_BIBINFO_BATCHSIZE + "}")
    private String batchSize;

    public Logger getLogger() {
        return logger;
    }

    public OngoingMatchingAlgorithmUtil getOngoingMatchingAlgorithmUtil() {
        return ongoingMatchingAlgorithmUtil;
    }

    public MatchingBibInfoDetailService getMatchingBibInfoDetailService() {
        return matchingBibInfoDetailService;
    }

    public DateUtil getDateUtil() {
        return dateUtil;
    }

    public String getBatchSize() {
        return batchSize;
    }

    @RequestMapping(value = "/ongoingMatchingJob")
    public String matchingJob(Model model) {
        model.addAttribute("matchingJobFromDate", new Date());
        return "ongoingMatchingJob";
    }

    @PostMapping(value = "/ongoingMatchingJob")
    @ResponseBody
    public String startMatchingAlgorithmJob(@Valid @ModelAttribute("solrIndexRequest") SolrIndexRequest solrIndexRequest) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Date date = solrIndexRequest.getCreatedDate();
        String jobType = solrIndexRequest.getProcessType();
        String status = "";
        Integer rows = Integer.valueOf(getBatchSize());
        try {
            if (jobType.equalsIgnoreCase(ScsbCommonConstants.ONGOING_MATCHING_ALGORITHM_JOB)) {
                status = getOngoingMatchingAlgorithmUtil().fetchUpdatedRecordsAndStartProcess(getDateUtil().getFromDate(date), rows);
            } else if (jobType.equalsIgnoreCase(ScsbConstants.POPULATE_DATA_FOR_DATA_DUMP_JOB)) {
                status = getMatchingBibInfoDetailService().populateMatchingBibInfo(getDateUtil().getFromDate(date), getDateUtil().getToDate(date));
            }
        } catch (Exception e) {
            logger.error("Exception : {0}", e);
        }
        stopWatch.stop();
        getLogger().info("Total Time taken to complete Ongoing Matching Algorithm : {}", stopWatch.getTotalTimeSeconds());
        return status;
    }
}
