package org.recap.executors;

import org.apache.commons.lang3.StringUtils;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbConstants;
import org.recap.repository.jpa.BibliographicDetailsRepository;
import org.recap.repository.jpa.HoldingsDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Created by chenchulakshmig on 21/6/16.
 */
@Service
public class BibItemIndexExecutorService extends IndexExecutorService {

    @Autowired
    private BibliographicDetailsRepository bibliographicDetailsRepository;

    @Autowired
    private HoldingsDetailsRepository holdingsDetailsRepository;

    @Resource(name = "recapSolrTemplate")
    private SolrTemplate solrTemplate;

    @Value("${" + PropertyKeyConstants.NONHOLDINGID_INSTITUTION + "}")
    private List<String> nonHoldingInstitutionList;

    /**
     * Gets the callable class for the thread to process.
     * @param coreName
     * @param pageNum
     * @param docsPerPage
     * @param owningInstitutionId
     * @param fromDate
     * @return
     */
    @Override
    public Callable getCallable(String coreName, int pageNum, int docsPerPage, Integer owningInstitutionId, Date fromDate, String partialIndexType, Map<String, Object> partialIndexMap) {
        return new BibItemIndexCallable(solrServerProtocol + solrUrl, coreName, pageNum, docsPerPage, bibliographicDetailsRepository, holdingsDetailsRepository,
                owningInstitutionId, fromDate, producerTemplate, solrTemplate, partialIndexType, partialIndexMap,nonHoldingInstitutionList);
    }

    /**
     * Gets the total documents count from database by owning institution id or by using from date.
     * @param owningInstitutionId
     * @param fromDate
     * @return
     */
    @Override
    protected Integer getTotalDocCount(Integer owningInstitutionId, Date fromDate) {
        Long count = 0L;
        if (null == owningInstitutionId && null == fromDate) {
            count = bibliographicDetailsRepository.count();
        } else if (null != owningInstitutionId && null == fromDate) {
            count = bibliographicDetailsRepository.countByOwningInstitutionId(owningInstitutionId);
        } else if (null == owningInstitutionId && null != fromDate) {
            count = bibliographicDetailsRepository.countByLastUpdatedDateAfter(fromDate);
        } else if (null != owningInstitutionId && null != fromDate) {
            count = bibliographicDetailsRepository.countByOwningInstitutionIdAndLastUpdatedDateAfter(owningInstitutionId, fromDate);
        }
        return count.intValue();
    }

    @Override
    protected Integer getTotalDocCountForPartialIndex(String partialIndexType, Map<String,Object> partialIndexMap) {
        Long count = 0L;
        if(StringUtils.isNotBlank(partialIndexType) && partialIndexMap != null) {
            if(partialIndexType.equalsIgnoreCase(ScsbConstants.BIB_ID_LIST)) {
                List<Integer> bibIdList = (List<Integer>) partialIndexMap.get(ScsbConstants.BIB_ID_LIST);
                count = bibliographicDetailsRepository.getCountOfBibBasedOnBibIds(bibIdList);
            } else if(partialIndexType.equalsIgnoreCase(ScsbConstants.BIB_ID_RANGE)) {
                String bibIdFrom = (String) partialIndexMap.get(ScsbConstants.BIB_ID_RANGE_FROM);
                String bibIdTo = (String) partialIndexMap.get(ScsbConstants.BIB_ID_RANGE_TO);
                count = bibliographicDetailsRepository.getCountOfBibBasedOnBibIdRange(Integer.valueOf(bibIdFrom), Integer.valueOf(bibIdTo));
            } else if(partialIndexType.equalsIgnoreCase(ScsbConstants.DATE_RANGE)) {
                Date dateFrom = (Date) partialIndexMap.get(ScsbConstants.DATE_RANGE_FROM);
                Date dateTo = (Date) partialIndexMap.get(ScsbConstants.DATE_RANGE_TO);
                count = bibliographicDetailsRepository.getCountOfBibBasedOnDateRange(dateFrom, dateTo);
            }
        }
        return count.intValue();
    }

    /**
     * This method sets bibliographic details repository.
     *
     * @param bibliographicDetailsRepository the bibliographic details repository
     */
    public void setBibliographicDetailsRepository(BibliographicDetailsRepository bibliographicDetailsRepository) {
        this.bibliographicDetailsRepository = bibliographicDetailsRepository;
    }
}