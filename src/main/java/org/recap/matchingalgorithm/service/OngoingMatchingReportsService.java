package org.recap.matchingalgorithm.service;

import com.google.common.collect.Lists;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.recap.ScsbConstants;
import org.recap.matchingalgorithm.MatchingCounter;
import org.recap.model.jpa.ReportDataEntity;
import org.recap.model.jpa.ReportEntity;
import org.recap.model.matchingreports.MatchingSerialAndMVMReports;
import org.recap.model.matchingreports.MatchingSummaryReport;
import org.recap.model.matchingreports.TitleExceptionReport;
import org.recap.model.search.SearchRecordsRequest;
import org.recap.repository.jpa.InstitutionDetailsRepository;
import org.recap.repository.jpa.ReportDetailRepository;
import org.recap.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.recap.ScsbConstants.MATCHING_COUNTER_OPEN;
import static org.recap.ScsbConstants.MATCHING_COUNTER_SHARED;

/**
 * Created by angelind on 21/6/17.
 */
@Service
public class OngoingMatchingReportsService {

    private static final Logger logger= LoggerFactory.getLogger(OngoingMatchingReportsService.class);

    @Autowired
    private ReportDetailRepository reportDetailRepository;

    @Autowired
    private DateUtil dateUtil;

    @Autowired
    private CsvUtil csvUtil;

    @Value("${" + PropertyKeyConstants.ONGOING_MATCHING_REPORT_DIRECTORY + "}")
    private String matchingReportsDirectory;

    @Resource(name = "recapSolrTemplate")
    private SolrTemplate solrTemplate;

    @Autowired
    private SolrQueryBuilder solrQueryBuilder;

    /**
     * The Camel context.
     */
    @Autowired
    CamelContext camelContext;

    /**
     * The Institution details repository.
     */
    @Autowired
    InstitutionDetailsRepository institutionDetailsRepository;

    @Autowired
    private CommonUtil commonUtil;

    /**
     * The Producer template.
     */
    @Autowired
    ProducerTemplate producerTemplate;

    @Autowired
    PropertyUtil propertyUtil;

    public static Logger getLogger() {
        return logger;
    }

    public ReportDetailRepository getReportDetailRepository() {
        return reportDetailRepository;
    }

    public DateUtil getDateUtil() {
        return dateUtil;
    }

    public CsvUtil getCsvUtil() {
        return csvUtil;
    }

    public String getMatchingReportsDirectory() {
        return matchingReportsDirectory;
    }

    public SolrTemplate getSolrTemplate() {
        return solrTemplate;
    }

    public SolrQueryBuilder getSolrQueryBuilder() {
        return solrQueryBuilder;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public InstitutionDetailsRepository getInstitutionDetailsRepository() {
        return institutionDetailsRepository;
    }

    public ProducerTemplate getProducerTemplate() {
        return producerTemplate;
    }

    /**
     * Generate title exception report string.
     *
     * @param createdDate the created date
     * @param batchSize   the batch size
     * @return the string
     */
    public String generateTitleExceptionReport(Date createdDate, Integer batchSize) {
        Page<ReportEntity> reportEntityPage = getReportDetailRepository().findByFileAndTypeAndDateRangeWithPaging(PageRequest.of(0, batchSize), ScsbCommonConstants.ONGOING_MATCHING_ALGORITHM, ScsbConstants.TITLE_EXCEPTION_TYPE,
                getDateUtil().getFromDate(createdDate), getDateUtil().getToDate(createdDate));
        int totalPages = reportEntityPage.getTotalPages();
        List<TitleExceptionReport> titleExceptionReports = new ArrayList<>();
        int maxTitleCount = 0;
        maxTitleCount = getTitleExceptionReport(reportEntityPage.getContent(), titleExceptionReports, maxTitleCount);
        for(int pageNum=1; pageNum<totalPages; pageNum++) {
            reportEntityPage = getReportDetailRepository().findByFileAndTypeAndDateRangeWithPaging(PageRequest.of(pageNum, batchSize), ScsbCommonConstants.ONGOING_MATCHING_ALGORITHM, ScsbConstants.TITLE_EXCEPTION_TYPE,
                    getDateUtil().getFromDate(createdDate), getDateUtil().getToDate(createdDate));
            maxTitleCount = getTitleExceptionReport(reportEntityPage.getContent(), titleExceptionReports, maxTitleCount);
        }
        File file = null;
        if(CollectionUtils.isNotEmpty(titleExceptionReports)) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(ScsbConstants.DATE_FORMAT_FOR_REPORTS);
                String formattedDate = sdf.format(new Date());
                String fileNameWithExtension = getMatchingReportsDirectory() + File.separator + ScsbConstants.TITLE_EXCEPTION_REPORT + ScsbConstants.UNDER_SCORE + formattedDate + ScsbConstants.CSV_EXTENSION;
                file = getCsvUtil().createTitleExceptionReportFile(fileNameWithExtension, maxTitleCount, titleExceptionReports);
                getCamelContext().getRouteController().startRoute(ScsbConstants.FTP_TITLE_EXCEPTION_REPORT_ROUTE_ID);
            } catch (Exception e) {
                getLogger().error(ScsbConstants.EXCEPTION_TEXT + ScsbConstants.LOGGER_MSG, e);
            }
        }
        return file != null ? file.getName() : null;
    }


    private int getTitleExceptionReport(List<ReportEntity> reportEntities, List<TitleExceptionReport> titleExceptionReports, int maxTitleCount) {
        if(CollectionUtils.isNotEmpty(reportEntities)) {
            for(ReportEntity reportEntity : reportEntities) {
                List<ReportDataEntity> reportDataEntities = new ArrayList<>();
                List<String> titleList = new ArrayList<>();
                for(ReportDataEntity reportDataEntity : reportEntity.getReportDataEntities()) {
                    String headerName = reportDataEntity.getHeaderName();
                    String headerValue = reportDataEntity.getHeaderValue();
                    if(headerName.contains("Title")) {
                        titleList.add(headerValue);
                    } else {
                        reportDataEntities.add(reportDataEntity);
                    }
                }
                int size = titleList.size();
                if(maxTitleCount < size) {
                    maxTitleCount = size;
                }
                OngoingMatchingAlgorithmReportGenerator ongoingMatchingAlgorithmReportGenerator = new OngoingMatchingAlgorithmReportGenerator();
                TitleExceptionReport titleExceptionReport = ongoingMatchingAlgorithmReportGenerator.prepareTitleExceptionReportRecord(reportDataEntities);
                titleExceptionReport.setTitleList(titleList);
                titleExceptionReports.add(titleExceptionReport);
            }
        }
        return maxTitleCount;
    }

    /**
     * Generate serial and mvms report.
     *
     * @param serialMvmBibIds the serial mvm bib ids
     */
    public void generateSerialAndMVMsReport(List<Integer> serialMvmBibIds) {
        List<MatchingSerialAndMVMReports> matchingSerialAndMvmReports = new ArrayList<>();
        if(CollectionUtils.isNotEmpty(serialMvmBibIds)) {
            List<List<Integer>> bibIdLists = Lists.partition(serialMvmBibIds, 100);
            for(List<Integer> bibIds : bibIdLists) {
                String bibIdQuery = ScsbCommonConstants.BIB_ID + ":" + "(" + StringUtils.join(bibIds, " ") + ")";
                SolrQuery solrQuery = new SolrQuery(bibIdQuery);
                String[] fieldNameList = {ScsbConstants.TITLE_SUBFIELD_A, ScsbCommonConstants.BIB_ID, ScsbCommonConstants.BIB_OWNING_INSTITUTION, ScsbCommonConstants.OWNING_INST_BIB_ID, ScsbCommonConstants.ROOT};
                solrQuery.setFields(fieldNameList);
                solrQuery.setRows(100);
                try {
                    QueryResponse queryResponse = getSolrTemplate().getSolrClient().query(solrQuery);
                    SolrDocumentList solrDocumentList = queryResponse.getResults();
                    for (Iterator<SolrDocument> iterator = solrDocumentList.iterator(); iterator.hasNext(); ) {
                        SolrDocument solrDocument = iterator.next();
                        matchingSerialAndMvmReports.addAll(getMatchingSerialAndMvmReports(solrDocument));
                    }
                } catch (Exception e) {
                    getLogger().error(ScsbConstants.EXCEPTION_TEXT + ScsbConstants.LOGGER_MSG, e);
                }
            }
        }
        if(CollectionUtils.isNotEmpty(matchingSerialAndMvmReports)) {
            try {
                getCamelContext().getRouteController().startRoute(ScsbConstants.FTP_SERIAL_MVM_REPORT_ROUTE_ID);
                getProducerTemplate().sendBodyAndHeader(ScsbConstants.FTP_SERIAL_MVM_REPORT_Q, matchingSerialAndMvmReports, ScsbConstants.FILE_NAME, ScsbConstants.MATCHING_SERIAL_MVM_REPORT);
            } catch (Exception e) {
                getLogger().error(ScsbConstants.EXCEPTION_TEXT + ScsbConstants.LOGGER_MSG, e);
            }
        }
    }

    private List<MatchingSerialAndMVMReports> getMatchingSerialAndMvmReports(SolrDocument solrDocument) {

        List<MatchingSerialAndMVMReports> matchingSerialAndMVMReportsList = new ArrayList<>();
        SolrQuery solrQueryForChildDocuments = getSolrQueryBuilder().getSolrQueryForBibItem(ScsbCommonConstants.ROOT + ":" + solrDocument.getFieldValue(ScsbCommonConstants.ROOT));
        solrQueryForChildDocuments.setFilterQueries(ScsbCommonConstants.DOCTYPE + ":" + "(\"" + ScsbCommonConstants.HOLDINGS + "\" \"" + ScsbCommonConstants.ITEM + "\")");
        String[] fieldNameList = {ScsbConstants.VOLUME_PART_YEAR, ScsbCommonConstants.HOLDING_ID, ScsbConstants.SUMMARY_HOLDINGS, ScsbCommonConstants.BARCODE,
                ScsbConstants.USE_RESTRICTION_DISPLAY, ScsbCommonConstants.ITEM_ID, ScsbCommonConstants.ROOT, ScsbCommonConstants.DOCTYPE, ScsbCommonConstants.HOLDINGS_ID,
                ScsbCommonConstants.IS_DELETED_ITEM, ScsbConstants.ITEM_CATALOGING_STATUS};
        solrQueryForChildDocuments.setFields(fieldNameList);
        solrQueryForChildDocuments.setSort(ScsbCommonConstants.DOCTYPE, SolrQuery.ORDER.asc);
        QueryResponse queryResponse = null;
        try {
            queryResponse = getSolrTemplate().getSolrClient().query(solrQueryForChildDocuments);
            SolrDocumentList solrDocuments = queryResponse.getResults();
            if (solrDocuments.getNumFound() > 10) {
                solrQueryForChildDocuments.setRows((int) solrDocuments.getNumFound());
                queryResponse = getSolrTemplate().getSolrClient().query(solrQueryForChildDocuments);
                solrDocuments = queryResponse.getResults();
            }
            Map<Integer, String> holdingsMap = new HashMap<>();
            for (Iterator<SolrDocument> iterator = solrDocuments.iterator(); iterator.hasNext(); ) {
                SolrDocument solrChildDocument =  iterator.next();
                String docType = (String) solrChildDocument.getFieldValue(ScsbCommonConstants.DOCTYPE);
                if(docType.equalsIgnoreCase(ScsbCommonConstants.HOLDINGS)) {
                    holdingsMap.put((Integer) solrChildDocument.getFieldValue(ScsbCommonConstants.HOLDING_ID),
                            String.valueOf(solrChildDocument.getFieldValue(ScsbConstants.SUMMARY_HOLDINGS)));
                }
                if(docType.equalsIgnoreCase(ScsbCommonConstants.ITEM)) {
                    boolean isDeletedItem = (boolean) solrChildDocument.getFieldValue(ScsbCommonConstants.IS_DELETED_ITEM);
                    String itemCatalogingStatus = String.valueOf(solrChildDocument.getFieldValue(ScsbConstants.ITEM_CATALOGING_STATUS));
                    if(!isDeletedItem && itemCatalogingStatus.equalsIgnoreCase(ScsbCommonConstants.COMPLETE_STATUS)) {
                        MatchingSerialAndMVMReports matchingSerialAndMVMReports = new MatchingSerialAndMVMReports();
                        matchingSerialAndMVMReports.setTitle(String.valueOf(solrDocument.getFieldValue(ScsbConstants.TITLE_SUBFIELD_A)));
                        matchingSerialAndMVMReports.setBibId(String.valueOf(solrDocument.getFieldValue(ScsbCommonConstants.BIB_ID)));
                        matchingSerialAndMVMReports.setOwningInstitutionId(String.valueOf(solrDocument.getFieldValue(ScsbCommonConstants.BIB_OWNING_INSTITUTION)));
                        matchingSerialAndMVMReports.setOwningInstitutionBibId(String.valueOf(solrDocument.getFieldValue(ScsbCommonConstants.OWNING_INST_BIB_ID)));
                        matchingSerialAndMVMReports.setBarcode(String.valueOf(solrChildDocument.getFieldValue(ScsbCommonConstants.BARCODE)));
                        matchingSerialAndMVMReports.setVolumePartYear(String.valueOf(solrChildDocument.getFieldValue(ScsbConstants.VOLUME_PART_YEAR)));
                        matchingSerialAndMVMReports.setUseRestriction(String.valueOf(solrChildDocument.getFieldValue(ScsbConstants.USE_RESTRICTION_DISPLAY)));
                        List<Integer> holdingsIds = (List<Integer>) solrChildDocument.getFieldValue(ScsbCommonConstants.HOLDINGS_ID);
                        Integer holdingsId = holdingsIds.get(0);
                        matchingSerialAndMVMReports.setSummaryHoldings(holdingsMap.get(holdingsId));
                        matchingSerialAndMVMReportsList.add(matchingSerialAndMVMReports);
                    }
                }
            }
        }catch (Exception e) {
            getLogger().error(ScsbConstants.EXCEPTION_TEXT + ScsbConstants.LOGGER_MSG, e);
        }
        return matchingSerialAndMVMReportsList;
    }

    /**
     * Populate summary report list.
     *
     * @return the list
     */
    public List<MatchingSummaryReport> populateSummaryReportBeforeMatching() {
        List<MatchingSummaryReport> matchingSummaryReports = new ArrayList<>();
        List<String> allInstitutionCodesExceptSupportInstitution = commonUtil.findAllInstitutionCodesExceptSupportInstitution();
        for (String institutionCode : allInstitutionCodesExceptSupportInstitution) {
            MatchingSummaryReport matchingSummaryReport = new MatchingSummaryReport();
            matchingSummaryReport.setInstitution(institutionCode);
            matchingSummaryReport.setOpenItemsBeforeMatching(String.valueOf(MatchingCounter.getSpecificInstitutionCounterMap(institutionCode).get(MATCHING_COUNTER_OPEN)));
            matchingSummaryReport.setSharedItemsBeforeMatching(String.valueOf(MatchingCounter.getSpecificInstitutionCounterMap(institutionCode).get(MATCHING_COUNTER_SHARED)));
            matchingSummaryReports.add(matchingSummaryReport);
        }
        return matchingSummaryReports;
    }

    /**
     * Generate summary report.
     *
     * @param matchingSummaryReports the matching summary reports
     */
    public void generateSummaryReport(List<MatchingSummaryReport> matchingSummaryReports) {
        SearchRecordsRequest searchRecordsRequest = new SearchRecordsRequest(propertyUtil.getAllInstitutions());
        Integer bibCount = 0;
        Integer itemCount = 0;
        SolrQuery bibCountQuery = getSolrQueryBuilder().getCountQueryForParentAndChildCriteria(searchRecordsRequest);
        SolrQuery itemCountQuery = getSolrQueryBuilder().getCountQueryForChildAndParentCriteria(searchRecordsRequest);
        bibCountQuery.setRows(0);
        itemCountQuery.setRows(0);
        try {
            QueryResponse queryResponseForBib = getSolrTemplate().getSolrClient().query(bibCountQuery);
            QueryResponse queryResponseForItem = getSolrTemplate().getSolrClient().query(itemCountQuery);
            bibCount = Math.toIntExact(queryResponseForBib.getResults().getNumFound());
            itemCount = Math.toIntExact(queryResponseForItem.getResults().getNumFound());
        } catch (Exception e) {
            getLogger().error(ScsbConstants.EXCEPTION_TEXT + ScsbConstants.LOGGER_MSG, e);
        }
        try {
            for(MatchingSummaryReport matchingSummaryReport : matchingSummaryReports) {
                matchingSummaryReport.setTotalBibs(String.valueOf(bibCount));
                matchingSummaryReport.setTotalItems(String.valueOf(itemCount));
                String openItemsAfterMatching = "";
                String sharedItemsAfterMatching = "";
                List<String> institutions = commonUtil.findAllInstitutionCodesExceptSupportInstitution();
                for (String institution : institutions) {
                    if (matchingSummaryReport.getInstitution().equalsIgnoreCase(institution)) {
                        openItemsAfterMatching = String.valueOf(MatchingCounter.getSpecificInstitutionCounterMap(institution).get(MATCHING_COUNTER_OPEN));
                        sharedItemsAfterMatching = String.valueOf(MatchingCounter.getSpecificInstitutionCounterMap(institution).get(MATCHING_COUNTER_SHARED));
                    }
                }
                String openItemsDiff = String.valueOf(Integer.valueOf(openItemsAfterMatching) - Integer.valueOf(matchingSummaryReport.getOpenItemsBeforeMatching()));
                String sharedItemsDiff = String.valueOf(Integer.valueOf(sharedItemsAfterMatching) - Integer.valueOf(matchingSummaryReport.getSharedItemsBeforeMatching()));
                matchingSummaryReport.setOpenItemsDiff(openItemsDiff);
                matchingSummaryReport.setSharedItemsDiff(sharedItemsDiff);
                matchingSummaryReport.setOpenItemsAfterMatching(openItemsAfterMatching);
                matchingSummaryReport.setSharedItemsAfterMatching(sharedItemsAfterMatching);
            }
            getCamelContext().getRouteController().startRoute(ScsbConstants.FTP_MATCHING_SUMMARY_REPORT_ROUTE_ID);
            getProducerTemplate().sendBodyAndHeader(ScsbConstants.FTP_MATCHING_SUMMARY_REPORT_Q, matchingSummaryReports, ScsbConstants.FILE_NAME, ScsbConstants.MATCHING_SUMMARY_REPORT);
        } catch (Exception e) {
            getLogger().error(ScsbConstants.EXCEPTION_TEXT + ScsbConstants.LOGGER_MSG, e);
        }
    }
}
