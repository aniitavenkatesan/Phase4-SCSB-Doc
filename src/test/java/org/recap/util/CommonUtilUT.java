package org.recap.util;

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
import org.recap.*;
import org.recap.model.jpa.BibliographicEntity;
import org.recap.model.jpa.CollectionGroupEntity;
import org.recap.model.jpa.HoldingsEntity;
import org.recap.model.jpa.InstitutionEntity;
import org.recap.model.jpa.ItemStatusEntity;
import org.recap.model.search.resolver.HoldingsValueResolver;
import org.recap.model.search.resolver.impl.item.ItemCreatedByValueResolver;
import org.recap.model.solr.BibItem;
import org.recap.model.solr.Item;
import org.recap.repository.jpa.CollectionGroupDetailsRepository;
import org.recap.repository.jpa.InstitutionDetailsRepository;
import org.recap.repository.jpa.ItemStatusDetailsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.solr.core.SolrTemplate;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by AnithaV on 01/10/20.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({SolrTemplate.class,SolrClient.class})
public class CommonUtilUT extends BaseTestCaseUT {

    @InjectMocks
    CommonUtil commonUtil;

    @Mock
    ItemStatusDetailsRepository itemStatusDetailsRepository;

    @Mock
    InstitutionDetailsRepository institutionDetailsRepository;

    @Mock
    CollectionGroupDetailsRepository collectionGroupDetailsRepository;

    @Value("${" + PropertyKeyConstants.SCSB_SUPPORT_INSTITUTION + "}")
    private String supportInstitution;

    @Before
    public  void setup(){
        MockitoAnnotations.initMocks(this);
    }


    @Test
    public void getItemStatusMap(){
        List<ItemStatusEntity> itemStatusEntities = new ArrayList<>();
        ItemStatusEntity itemStatusEntity = new ItemStatusEntity();
        itemStatusEntity.setId(1);
        itemStatusEntity.setStatusCode("SUCCESS");
        itemStatusEntity.setStatusDescription("AVAILABLE");
        itemStatusEntities.add(itemStatusEntity);
        Mockito.when(itemStatusDetailsRepository.findAll()).thenReturn(itemStatusEntities);
        commonUtil.getItemStatusMap();
        assertNotNull(commonUtil);
    }

    @Test
    public void getItemStatusMapException(){
        Mockito.when(itemStatusDetailsRepository.findAll()).thenThrow(NullPointerException.class);
        commonUtil.getItemStatusMap();
        Mockito.when(collectionGroupDetailsRepository.findAll()).thenThrow(NullPointerException.class);
        commonUtil.getCollectionGroupMap();
        assertNotNull(commonUtil);
    }

    @Test
    public void buildHoldingsEntity(){
        BibliographicEntity bibliographicEntity=new BibliographicEntity();
        StringBuilder errorMessage=new StringBuilder();
        HoldingsEntity response=commonUtil.buildHoldingsEntity(bibliographicEntity,new Date(),errorMessage,"");
        assertEquals("Holdings Content cannot be empty,",errorMessage.toString());
    }

    @Test
    public void buildHoldingsEntityEmptyResponse(){
        BibliographicEntity bibliographicEntity=new BibliographicEntity();
        StringBuilder errorMessage=new StringBuilder();
        HoldingsEntity emptyResponse=commonUtil.buildHoldingsEntity(bibliographicEntity,new Date(),errorMessage,"test");
        assertEquals("",errorMessage.toString());
    }

    @Test
    public void addHoldingsEntityToMap(){
        HoldingsEntity holdingsEntity=new HoldingsEntity();
        String owningid="fgrtf-1234fgrtf-1234fgrtf-1234fgrtf-1234fgrtf-1234fgrtf-1234fgrtf-1234fgrtf-1234fgrtf-1234fgrtf-1234fgrtf-1234";
        Map<String, Object> response=commonUtil.addHoldingsEntityToMap(new HashMap<>(),holdingsEntity,"");
        Map<String, Object> response1=commonUtil.addHoldingsEntityToMap(new HashMap<>(),holdingsEntity,owningid);
        assertEquals(holdingsEntity,response1.get("holdingsEntity"));
    }

    @Test
    public void getBibItemFromSolrFieldNames(){
        SolrDocument solrDocument=new SolrDocument();
        solrDocument.setField("_root_","root");
        Collection<String> fieldNames=new ArrayList<>();
        fieldNames.add("_root_");
        BibItem bibItems=new BibItem();
        bibItems.setRoot("_root_");
        BibItem bibItem= commonUtil.getBibItemFromSolrFieldNames(solrDocument,fieldNames,bibItems);
        assertEquals("root",bibItem.getRoot());
    }

    @Test
    public void getItem(){
        SolrDocument solrDocument = getEntries();
        List<HoldingsValueResolver> holdingsValueResolvers= commonUtil.getHoldingsValueResolvers();
        assertNotNull(holdingsValueResolvers);
        Item item= commonUtil.getItem(solrDocument);
        assertEquals("123",item.getRoot());

    }

    @Test
    public void getAllInstitutionCodes(){
        List<String> institutionCodes= Arrays.asList(ScsbCommonConstants.PRINCETON);
        Mockito.when(institutionDetailsRepository.findAll()).thenReturn(getInstitutionEntities());
        List<String> response= commonUtil.getAllInstitutionCodes();
        Map response1= commonUtil.getInstitutionEntityMap();
        assertNotNull(response1);
        assertEquals(institutionCodes,response);

    }

    @Test
    public void getCollectionGroupMap(){
        Mockito.when(collectionGroupDetailsRepository.findAll()).thenReturn(getCollectionGroupEntities());
        Map response= commonUtil.getCollectionGroupMap();
        assertNotNull(response);

    }

    private List<CollectionGroupEntity> getCollectionGroupEntities() {
        CollectionGroupEntity collectionGroupEntity = TestUtil.getCollectionGroupEntities(1,"Shared","Shared");
        List<CollectionGroupEntity> collectionGroupEntityList = new ArrayList<>();
        collectionGroupEntityList.add(collectionGroupEntity);
        return collectionGroupEntityList;
    }

    private List<InstitutionEntity> getInstitutionEntities() {
        List<InstitutionEntity> institutionEntities = new ArrayList<>();
        InstitutionEntity institutionEntity = TestUtil.getInstitutionEntity(1,"PUL","Princeton");
        institutionEntities.add(institutionEntity);
        return institutionEntities;
    }
    @Test
    public void getAllInstitutionCodesException(){
        List<String> institutionCodes= Arrays.asList();
        Mockito.when(institutionDetailsRepository.findAll()).thenThrow(NullPointerException.class);
        commonUtil.getInstitutionEntityMap();
        List<String> response= commonUtil.getAllInstitutionCodes();
        assertEquals(institutionCodes,response);

    }

    @Test
    public void getSolrDocumentsByDocType() throws IOException, SolrServerException {
        SolrDocumentList solrDocumentList =new SolrDocumentList();
        SolrDocument solrDocument = new SolrDocument();
        solrDocumentList.add(solrDocument);
        solrDocumentList.setNumFound(11l);
        SolrTemplate mocksolrTemplate1 = PowerMockito.mock(SolrTemplate.class);
        SolrClient solrClient=PowerMockito.mock(SolrClient.class);
        QueryResponse queryResponse=Mockito.mock(QueryResponse.class);
        PowerMockito.when(mocksolrTemplate1.getSolrClient()).thenReturn(solrClient);
        Mockito.when(solrClient.query(Mockito.any(SolrQuery.class))).thenReturn(queryResponse);
        Mockito.when(queryResponse.getResults()).thenReturn(solrDocumentList);
        SolrQuery solrQuery = new SolrQuery("testquery");
        solrQuery.setStart(1);
        solrQuery.setRows(1);
        SolrDocumentList solrDocumentListResult= commonUtil.getSolrDocumentsByDocType(solrQuery,mocksolrTemplate1);
        assertEquals(solrDocumentList,solrDocumentListResult);
    }

    private SolrDocument getEntries() {
        SolrDocument solrDocument=new SolrDocument();
        solrDocument.setField("_root_","123");
        solrDocument.setField("Availability_search","1");
        solrDocument.setField(ScsbCommonConstants.IS_DELETED_ITEM,true);
        solrDocument.setField("CollectionGroupDesignation","CollectionGroupDesignation");
        solrDocument.setField("CustomerCode","123456");
        solrDocument.setField("DocType","DocType");
        solrDocument.setField("ItemOwningInstitution","ItemOwningInstitution");
        solrDocument.setField("ItemCreatedDate",new Date());
        solrDocument.setField("ItemId",1);
        solrDocument.setField("ItemLastUpdatedBy","ItemLastUpdatedBy");
        solrDocument.setField("ItemLastUpdatedDate",new Date());
        solrDocument.setField(ScsbCommonConstants.HOLDINGS_ID,Arrays.asList(1));
        solrDocument.setField("VolumePartYear","true");
        solrDocument.setField("UseRestriction_display","true");
        solrDocument.setField("UseRestriction_search","true");
        solrDocument.setField("Availability_display","true");
        solrDocument.setField("CallNumber_display","true");
        solrDocument.setField("CallNumber_search","true");
        solrDocument.setField(ScsbConstants.OWNING_INSTITUTION_ITEM_ID,"true");
        solrDocument.setField("Barcode","true");
        ItemCreatedByValueResolver ItemCreatedByValueResolver = new ItemCreatedByValueResolver();
        ItemCreatedByValueResolver.setValue(new Item(),"true");
        ItemCreatedByValueResolver.isInterested("ItemCreatedBy");
        return solrDocument;
    }

    @Test
    public void findAllInstitutionCodesExceptSupportInstitution() {
        InstitutionEntity institutionEntity = getInstitutionEntity();
        Mockito.when(institutionDetailsRepository.findAllInstitutionCodesExceptSupportInstitution(supportInstitution)).thenReturn(Collections.singletonList(institutionEntity.getInstitutionName()));
        List<String> result = commonUtil.findAllInstitutionCodesExceptSupportInstitution();
        assertNotNull(result);
    }

    @Test
    public void findAllInstitutionsExceptSupportInstitution() {
        InstitutionEntity institutionEntity = getInstitutionEntity();
        Mockito.when(institutionDetailsRepository.findAllInstitutionsExceptSupportInstitution(supportInstitution)).thenReturn(Collections.singletonList(institutionEntity));
        List<InstitutionEntity> result = commonUtil.findAllInstitutionsExceptSupportInstitution();
        assertNotNull(result);
    }

    private InstitutionEntity getInstitutionEntity() {
        InstitutionEntity institutionEntity = new InstitutionEntity();
        institutionEntity.setId(1);
        institutionEntity.setInstitutionCode("PUL");
        institutionEntity.setInstitutionName("PUL");
        return institutionEntity;
    }
}
