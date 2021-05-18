
package org.recap.repository.solr.impl;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.recap.BaseTestCaseUT;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.recap.ScsbConstants;
import org.recap.model.jpa.BibliographicEntity;
import org.recap.model.jpa.HoldingsEntity;
import org.recap.model.jpa.ItemEntity;
import org.recap.model.search.SearchRecordsRequest;
import org.recap.model.solr.BibItem;
import org.recap.repository.jpa.BibliographicDetailsRepository;
import org.recap.util.CommonUtil;
import org.recap.util.SolrQueryBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Created by premkb on 27/1/17.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({SolrTemplate.class, SolrClient.class})
@PowerMockRunnerDelegate(SpringJUnit4ClassRunner.class)
public class DataDumpSolrDocumentRepositoryImplAT extends BaseTestCaseUT {

    @InjectMocks
    private DataDumpSolrDocumentRepositoryImpl dataDumpSolrDocumentRepository;

    @Mock
    private SolrQueryBuilder solrQueryBuilder;

    @Value("${" + PropertyKeyConstants.ETL_DATA_DUMP_DELETED_TYPE_ONLYORPHAN_INSTITUTION + "}")
    private String deletedOnlyOrphanInstitution;

    @Value("${" + PropertyKeyConstants.ETL_DATA_DUMP_INCREMENTAL_TYPE_NONFULLTREE_INSTITUTION + "}")
    private String incrementalNonFullTreeInstitution;

    @Mock
    private CommonUtil commonUtil;

    @Mock
    private BibliographicDetailsRepository bibliographicDetailsRepository;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(dataDumpSolrDocumentRepository, "deletedOnlyOrphanInstitution", "PUL,CUL");
        ReflectionTestUtils.setField(dataDumpSolrDocumentRepository, "incrementalNonFullTreeInstitution", incrementalNonFullTreeInstitution);
    }

    @Test
    public void searchByItem() throws Exception{
        SearchRecordsRequest searchRecordsRequest = new SearchRecordsRequest();
        searchRecordsRequest.getOwningInstitutions().addAll(Arrays.asList("CUL", "PUL"));
        searchRecordsRequest.getMaterialTypes().addAll(Arrays.asList("Monograph", "Serial", "Other"));
        searchRecordsRequest.setDeleted(true);
        Map<Integer, BibItem> bibItemMap = new HashMap<>();
        SolrQuery queryForChildAndParentCriteria= new SolrQuery();
        Mockito.when(solrQueryBuilder.getDeletedQueryForDataDump(searchRecordsRequest,true)).thenReturn(queryForChildAndParentCriteria);
        SolrTemplate mocksolrTemplate1 = PowerMockito.mock(SolrTemplate.class);
        SolrClient solrClient=PowerMockito.mock(SolrClient.class);
        ReflectionTestUtils.setField(dataDumpSolrDocumentRepository,"solrTemplate",mocksolrTemplate1);
        PowerMockito.when(mocksolrTemplate1.getSolrClient()).thenReturn(solrClient);
        QueryResponse queryResponse= Mockito.mock(QueryResponse.class);
        Mockito.when(solrClient.query(Mockito.any(SolrQuery.class))).thenReturn(queryResponse);
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        SolrDocument solrDocument = new SolrDocument();
        solrDocument.setField("_root_","root");
        solrDocumentList.add(solrDocument);
        solrDocumentList.setNumFound(1);
        Mockito.when(queryResponse.getResults()).thenReturn(solrDocumentList);
        dataDumpSolrDocumentRepository.searchByItem(searchRecordsRequest,true,bibItemMap);
        assertTrue(bibItemMap.isEmpty());
    }

    @Test
    public void searchByItemForDeleted() throws Exception{
        SearchRecordsRequest searchRecordsRequest=new SearchRecordsRequest();
        searchRecordsRequest.getOwningInstitutions().addAll(Arrays.asList("CUL", "PUL"));
        searchRecordsRequest.getMaterialTypes().addAll(Arrays.asList("Monograph", "Serial", "Other"));
        searchRecordsRequest.setDeleted(true);
        SolrQuery queryForParentAndChildCriteria=new SolrQuery();
        Mockito.when(solrQueryBuilder.getQueryForParentAndChildCriteriaForDataDump(Mockito.any())).thenReturn(queryForParentAndChildCriteria);
        Mockito.when(solrQueryBuilder.getDeletedQueryForDataDump(searchRecordsRequest,true)).thenReturn(queryForParentAndChildCriteria);
        Mockito.when(solrQueryBuilder.getDeletedQueryForDataDump(searchRecordsRequest,false)).thenReturn(queryForParentAndChildCriteria);
        Mockito.when(solrQueryBuilder.getQueryForParentAndChildCriteriaForDeletedDataDump(searchRecordsRequest)).thenReturn(queryForParentAndChildCriteria);
        SolrTemplate mocksolrTemplate1 = PowerMockito.mock(SolrTemplate.class);
        SolrClient solrClient=PowerMockito.mock(SolrClient.class);
        ReflectionTestUtils.setField(dataDumpSolrDocumentRepository,"solrTemplate",mocksolrTemplate1);
        PowerMockito.when(mocksolrTemplate1.getSolrClient()).thenReturn(solrClient);
        QueryResponse queryResponse= Mockito.mock(QueryResponse.class);
        Mockito.when(solrClient.query(Mockito.any(SolrQuery.class))).thenReturn(queryResponse);
        SolrDocumentList solrDocumentList = getSolrDocumentList();
        SolrDocument solrDocument = new SolrDocument();
        solrDocument.setField("_root_","root");
        solrDocumentList.add(solrDocument);
        solrDocumentList.setNumFound(1);
        Mockito.when(queryResponse.getResults()).thenReturn(solrDocumentList);
        Mockito.when(commonUtil.getSolrDocumentsByDocType(null,mocksolrTemplate1)).thenReturn(solrDocumentList);
        BibliographicEntity bibliographicEntity=getBibliographicEntity();
        Mockito.when(bibliographicDetailsRepository.findById(Mockito.anyInt())).thenReturn(Optional.ofNullable(bibliographicEntity));
        Map<String, Object> search=dataDumpSolrDocumentRepository.search(searchRecordsRequest);
        assertEquals(true,search.containsKey(ScsbCommonConstants.SEARCH_SUCCESS_RESPONSE));
    }


    @Test
    public void searchByItemForDeletedonlyOrphan() throws Exception{
        SearchRecordsRequest searchRecordsRequest=new SearchRecordsRequest();
        searchRecordsRequest.setRequestingInstitution("PUL");
        searchRecordsRequest.setFieldValue("root");
        searchRecordsRequest.setFieldName("test");
        searchRecordsRequest.setDeleted(true);
        SolrQuery queryForParentAndChildCriteria=new SolrQuery();
        Mockito.when(solrQueryBuilder.getQueryForParentAndChildCriteriaForDataDump(Mockito.any())).thenReturn(queryForParentAndChildCriteria);
        Mockito.when(solrQueryBuilder.getDeletedQueryForDataDump(searchRecordsRequest,true)).thenReturn(queryForParentAndChildCriteria);
        Mockito.when(solrQueryBuilder.getDeletedQueryForDataDump(searchRecordsRequest,false)).thenReturn(queryForParentAndChildCriteria);
        Mockito.when(solrQueryBuilder.getQueryForParentAndChildCriteriaForDeletedDataDump(searchRecordsRequest)).thenReturn(queryForParentAndChildCriteria);
        SolrTemplate mocksolrTemplate1 = PowerMockito.mock(SolrTemplate.class);
        SolrClient solrClient=PowerMockito.mock(SolrClient.class);
        ReflectionTestUtils.setField(dataDumpSolrDocumentRepository,"solrTemplate",mocksolrTemplate1);
        PowerMockito.when(mocksolrTemplate1.getSolrClient()).thenReturn(solrClient);
        QueryResponse queryResponse= Mockito.mock(QueryResponse.class);
        Mockito.when(solrClient.query(Mockito.any(SolrQuery.class))).thenReturn(queryResponse);
        SolrDocumentList solrDocumentList = getSolrDocumentList();
        BibliographicEntity bibliographicEntity=getBibliographicEntity();
        Mockito.when(bibliographicDetailsRepository.findById(Mockito.anyInt())).thenReturn(Optional.ofNullable(bibliographicEntity));
        Mockito.when(queryResponse.getResults()).thenReturn(solrDocumentList);
        Mockito.when(commonUtil.getSolrDocumentsByDocType(null,mocksolrTemplate1)).thenReturn(solrDocumentList);
        Map<String, Object> search=dataDumpSolrDocumentRepository.search(searchRecordsRequest);
        assertEquals(true,search.containsKey(ScsbCommonConstants.SEARCH_SUCCESS_RESPONSE));
    }

    @Test
    public void searchByBib() throws Exception{
        SearchRecordsRequest searchRecordsRequest=new SearchRecordsRequest();
        searchRecordsRequest.setRequestingInstitution("NYPL");
        searchRecordsRequest.setFieldValue(ScsbConstants.INCREMENTAL_DUMP_TO_NOW);
        searchRecordsRequest.setFieldName(ScsbConstants.BIBITEM_LASTUPDATED_DATE);
        SolrQuery queryForParentAndChildCriteria=new SolrQuery();
        Mockito.when(solrQueryBuilder.getQueryForParentAndChildCriteriaForDataDump(Mockito.any())).thenReturn(queryForParentAndChildCriteria);
        SolrTemplate mocksolrTemplate1 = PowerMockito.mock(SolrTemplate.class);
        SolrClient solrClient=PowerMockito.mock(SolrClient.class);
        ReflectionTestUtils.setField(dataDumpSolrDocumentRepository,"solrTemplate",mocksolrTemplate1);
        PowerMockito.when(mocksolrTemplate1.getSolrClient()).thenReturn(solrClient);
        QueryResponse queryResponse= Mockito.mock(QueryResponse.class);
        Mockito.when(solrClient.query(Mockito.any(SolrQuery.class))).thenReturn(queryResponse);
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        SolrDocument solrDocument = new SolrDocument();
        solrDocument.setField("_root_","root");
        solrDocumentList.add(solrDocument);
        solrDocumentList.setNumFound(1);
        Mockito.when(queryResponse.getResults()).thenReturn(solrDocumentList);
        Mockito.when(commonUtil.getSolrDocumentsByDocType(null,mocksolrTemplate1)).thenReturn(solrDocumentList);
        Map<String, Object> search=dataDumpSolrDocumentRepository.search(searchRecordsRequest);
        assertEquals(true,search.containsKey(ScsbCommonConstants.SEARCH_SUCCESS_RESPONSE));
    }

    @Test
    public void searchByBibException() throws Exception{
        SearchRecordsRequest searchRecordsRequest=new SearchRecordsRequest();
        searchRecordsRequest.setRequestingInstitution("NYPL");
        searchRecordsRequest.setFieldValue(ScsbConstants.INCREMENTAL_DUMP_TO_NOW);
        searchRecordsRequest.setFieldName(ScsbConstants.BIBITEM_LASTUPDATED_DATE);
        SolrQuery queryForParentAndChildCriteria=new SolrQuery();
        Mockito.when(solrQueryBuilder.getQueryForParentAndChildCriteriaForDataDump(Mockito.any())).thenReturn(queryForParentAndChildCriteria);
        SolrTemplate mocksolrTemplate1 = PowerMockito.mock(SolrTemplate.class);
        SolrClient solrClient=PowerMockito.mock(SolrClient.class);
        ReflectionTestUtils.setField(dataDumpSolrDocumentRepository,"solrTemplate",mocksolrTemplate1);
        PowerMockito.when(mocksolrTemplate1.getSolrClient()).thenReturn(solrClient);
        Mockito.when(solrClient.query(Mockito.any(SolrQuery.class))).thenThrow(SolrServerException.class);
        Map<String, Object> search=dataDumpSolrDocumentRepository.search(searchRecordsRequest);
        assertNull(dataDumpSolrDocumentRepository.getPageNumberOnPageSizeChange(searchRecordsRequest));
        assertEquals(true,search.containsKey(ScsbCommonConstants.SEARCH_ERROR_RESPONSE));
    }

    private SolrDocumentList getSolrDocumentList() {
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        solrDocumentList.add(getEntries("_root_","root"));
        solrDocumentList.add(getEntries("ItemId",1));
        solrDocumentList.add(getEntries("DocType","true"));
        solrDocumentList.add(getEntries("id","id"));
        solrDocumentList.add(getEntries(ScsbCommonConstants.IS_DELETED_ITEM,true));
        solrDocumentList.add(getEntries("ItemBibId",Arrays.asList(2)));
        solrDocumentList.setNumFound(1);
        return  solrDocumentList;
    }

    private SolrDocument getEntries(String name,Object value) {
        SolrDocument solrDocument = new SolrDocument();
        solrDocument.setField(name,value);
        return solrDocument;
    }

    public BibliographicEntity getBibliographicEntity()  {
        BibliographicEntity bibliographicEntity = new BibliographicEntity();
        bibliographicEntity.setContent("sourceBibContent".getBytes());
        bibliographicEntity.setCreatedDate(new Date());
        bibliographicEntity.setLastUpdatedDate(new Date());
        bibliographicEntity.setCreatedBy("tst");
        bibliographicEntity.setLastUpdatedBy("tst");
        bibliographicEntity.setOwningInstitutionId(1);
        bibliographicEntity.setOwningInstitutionBibId("1421");
        bibliographicEntity.setId(1);
        bibliographicEntity.setDeleted(true);
        List<BibliographicEntity> bibliographicEntitylist = new LinkedList(Arrays.asList(bibliographicEntity));


        HoldingsEntity holdingsEntity = new HoldingsEntity();
        holdingsEntity.setContent("sourceHoldingsContent".getBytes());
        holdingsEntity.setCreatedDate(new Date());
        holdingsEntity.setLastUpdatedDate(new Date());
        holdingsEntity.setCreatedBy("tst");
        holdingsEntity.setOwningInstitutionId(1);
        holdingsEntity.setLastUpdatedBy("tst");
        holdingsEntity.setOwningInstitutionHoldingsId("1621");
        holdingsEntity.setId(1);
        List<HoldingsEntity> holdingsEntitylist = new LinkedList(Arrays.asList(holdingsEntity));

        ItemEntity itemEntity = new ItemEntity();
        itemEntity.setLastUpdatedDate(new Date());
        itemEntity.setOwningInstitutionItemId("6320902");
        itemEntity.setOwningInstitutionId(1);
        itemEntity.setBarcode("32101086866140");
        itemEntity.setCallNumber("x.12321");
        itemEntity.setCollectionGroupId(1);
        itemEntity.setCallNumberType("1");
        itemEntity.setCustomerCode("PA");
        itemEntity.setCreatedDate(new Date());
        itemEntity.setCreatedBy("tst");
        itemEntity.setLastUpdatedBy("tst");
        itemEntity.setItemAvailabilityStatusId(1);
        itemEntity.setId(1);
        itemEntity.setCgdChangeLog(ScsbConstants.CGD_CHANGE_LOG_OPEN_TO_PRIVATE);
        List<ItemEntity> itemEntitylist = new LinkedList(Arrays.asList(itemEntity));

        holdingsEntity.setBibliographicEntities(bibliographicEntitylist);
        holdingsEntity.setItemEntities(itemEntitylist);
        bibliographicEntity.setHoldingsEntities(holdingsEntitylist);
        bibliographicEntity.setItemEntities(itemEntitylist);
        itemEntity.setHoldingsEntities(holdingsEntitylist);
        itemEntity.setBibliographicEntities(bibliographicEntitylist);
        return bibliographicEntity;
    }
}
