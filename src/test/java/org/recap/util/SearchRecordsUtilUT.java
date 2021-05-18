package org.recap.util;

import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.recap.BaseTestCaseUT;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.recap.model.search.DataDumpSearchResult;
import org.recap.model.search.SearchRecordsRequest;
import org.recap.model.search.SearchResultRow;
import org.recap.model.solr.BibItem;
import org.recap.model.solr.Holdings;
import org.recap.model.solr.Item;
import org.recap.repository.solr.main.BibSolrDocumentRepository;
import org.recap.repository.solr.main.DataDumpSolrDocumentRepository;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by hemalathas on 24/2/17.
 */
public class SearchRecordsUtilUT extends BaseTestCaseUT {

    @InjectMocks
    SearchRecordsUtil searchRecordsUtil;

    @Mock
    BibSolrDocumentRepository bibSolrDocumentRepository;

    @Mock
    DataDumpSolrDocumentRepository dataDumpSolrDocumentRepository;

    @Mock
    PropertyUtil propertyUtil;

    @Value("${" + PropertyKeyConstants.SCSB_SUPPORT_INSTITUTION + "}")
    private String supportInstitution;

    @Test
    public void searchRecords() throws Exception {
        SearchRecordsRequest searchRecordsRequest=new SearchRecordsRequest();
        Map<String, Object> searchResponse=new HashMap<>();
        List<BibItem> bibItems = getBibItemList();
        searchResponse.put(ScsbCommonConstants.SEARCH_SUCCESS_RESPONSE,bibItems);
        Mockito.when(bibSolrDocumentRepository.search(Mockito.any(SearchRecordsRequest.class))).thenReturn(searchResponse);
        Mockito.when(propertyUtil.getAllInstitutions()).thenReturn(Arrays.asList("PUL","CUL","NYPL","HL", supportInstitution));

        List<SearchResultRow> searchRecords = searchRecordsUtil.searchRecords(searchRecordsRequest);
        assertNotNull(searchRecords);
    }

    @Test
    public void searchRecordsCollectionEmptySearch() throws Exception {
        SearchRecordsRequest searchRecordsRequest=new SearchRecordsRequest();
        searchRecordsRequest.setOwningInstitutions(Arrays.asList());
        searchRecordsRequest.setAvailability(Arrays.asList());
        searchRecordsRequest.setCollectionGroupDesignations(Arrays.asList());
        searchRecordsRequest.setUseRestrictions(Arrays.asList());
        Map<String, Object> searchResponse=new HashMap<>();
        List<BibItem> bibItems = getBibItemList();
        searchResponse.put(ScsbCommonConstants.SEARCH_SUCCESS_RESPONSE,bibItems);
        Mockito.when(bibSolrDocumentRepository.search(Mockito.any(SearchRecordsRequest.class))).thenReturn(searchResponse);
        List<SearchResultRow> searchRecords = searchRecordsUtil.searchRecords(searchRecordsRequest);
        assertNotNull(searchRecords);
    }

    @Test
    public void searchRecordselse() throws Exception {
        SearchRecordsRequest searchRecordsRequest=new SearchRecordsRequest();
        Map<String, Object> searchResponse=new HashMap<>();
        List<BibItem> bibItems = new ArrayList<>();
        BibItem bibItem = new BibItem();
        bibItem.setBibId(1);
        bibItem.setTitle("Title1");
        bibItem.setAuthorDisplay("Author1");
        bibItem.setBarcode("BC234");
        bibItem.setDocType("Bib");
        bibItem.setImprint("sample imprint");
        List<String> isbnList = new ArrayList<>();
        isbnList.add("978-3-16-148410-0");
        bibItem.setIsbn(isbnList);
        bibItem.setLccn("sample lccn");
        bibItem.setPublicationPlace("Texas");
        bibItem.setPublisher("McGraw Hill");
        bibItem.setPublicationDate("1998");
        bibItem.setSubject("Physics");
        bibItem.setNotes("Notes");
        bibItem.setOwningInstitution("PUL");
        bibItem.setOwningInstitutionBibId("1");
        bibItem.setMaterialType(ScsbCommonConstants.SERIAL);
        List<Item> items = new ArrayList<>();
        Item item = new Item();
        item.setItemId(1);
        item.setBarcode("BC234");
        item.setCallNumberSearch("123");
        item.setVolumePartYear("V1");
        item.setCustomerCode("NA");
        item.setAvailability(ScsbCommonConstants.NOT_AVAILABLE);
        item.setAvailabilityDisplay(ScsbCommonConstants.NOT_AVAILABLE);
        items.add(item);
        Item item1 = new Item();
        item1.setItemId(1);
        item1.setBarcode("BC234");
        item1.setCallNumberSearch("123");
        item1.setVolumePartYear("V1");
        item1.setCustomerCode("NA");
        item1.setAvailability(ScsbCommonConstants.NOT_AVAILABLE);
        item1.setAvailabilityDisplay("1");
        items.add(item);
        items.add(item1);
        bibItem.setItems(items);
        bibItems.add(bibItem);
        searchResponse.put(ScsbCommonConstants.SEARCH_SUCCESS_RESPONSE,bibItems);
        Mockito.when(bibSolrDocumentRepository.search(Mockito.any(SearchRecordsRequest.class))).thenReturn(searchResponse);
        List<SearchResultRow> searchRecords = searchRecordsUtil.searchRecords(searchRecordsRequest);
        assertNotNull(searchRecords);
    }

    @Test
    public void searchRecordsEmpty() throws Exception {
        SearchRecordsRequest searchRecordsRequest=new SearchRecordsRequest();
        searchRecordsRequest.setAvailability(new ArrayList<>());
        searchRecordsRequest.setMaterialTypes(new ArrayList<>());
        searchRecordsRequest.setCollectionGroupDesignations(new ArrayList<>());
        searchRecordsRequest.setOwningInstitutions(new ArrayList<>());
        searchRecordsRequest.setUseRestrictions(new ArrayList<>());
        List<SearchResultRow> searchRecordsEmpty = searchRecordsUtil.searchRecords(searchRecordsRequest);
        assertNotNull(searchRecordsEmpty);
    }

    @Test
    public void searchRecordsError() throws Exception {
        SearchRecordsRequest searchRecordsRequest=new SearchRecordsRequest();
        Map<String, Object> searchResponse=new HashMap<>();
        searchResponse.put(ScsbCommonConstants.SEARCH_ERROR_RESPONSE,"testError");
        Mockito.when(bibSolrDocumentRepository.search(Mockito.any())).thenReturn(searchResponse);
        Mockito.when(propertyUtil.getAllInstitutions()).thenReturn(Arrays.asList("PUL","CUL","NYPL","HL", supportInstitution));
        List<SearchResultRow> searchRecords = searchRecordsUtil.searchRecords(searchRecordsRequest);
        assertNotNull(searchRecords);
    }

    @Test
    public void searchRecordsForDataDump() throws Exception {
        SearchRecordsRequest searchRecordsRequest=new SearchRecordsRequest();
        Map<String, Object> searchResponse=new HashMap<>();
        searchResponse.put(ScsbCommonConstants.SEARCH_ERROR_RESPONSE,"testError");
        Mockito.when(searchRecordsUtil.getDataDumpSolrDocumentRepository().search(Mockito.any())).thenReturn(searchResponse);
        List<DataDumpSearchResult> searchRecords = searchRecordsUtil.searchRecordsForDataDump(searchRecordsRequest);
        assertNotNull(searchRecords);
    }

    @Test
    public void searchRecordsForDataDumpSuccess() throws Exception {
        SearchRecordsRequest searchRecordsRequest=new SearchRecordsRequest();
        Map<String, Object> searchResponse=new HashMap<>();
        searchResponse.put(ScsbCommonConstants.SEARCH_ERROR_RESPONSE,null);
        Mockito.when(searchRecordsUtil.getDataDumpSolrDocumentRepository().search(Mockito.any())).thenReturn(searchResponse);
        List<DataDumpSearchResult> searchRecords = searchRecordsUtil.searchRecordsForDataDump(searchRecordsRequest);
        assertNotNull(searchRecords);
    }


    @Test
    public void testBuildResultsForDataDump(){
        List<DataDumpSearchResult> dataDumpSearchResults = searchRecordsUtil.buildResultsForDataDump(getBibItemList());
        assertNotNull(dataDumpSearchResults);
        assertNotNull(dataDumpSearchResults.get(0).getBibId());
        assertNotNull(dataDumpSearchResults.get(0).getItemIds());
    }

    public List<BibItem> getBibItemList(){
        List<BibItem> bibItems = new ArrayList<>();
        BibItem bibItem = new BibItem();
        bibItem.setBibId(1);
        bibItem.setTitle("Title1");
        bibItem.setAuthorDisplay("Author1");
        bibItem.setBarcode("BC234");
        bibItem.setDocType("Bib");
        bibItem.setImprint("sample imprint");
        List<String> isbnList = new ArrayList<>();
        isbnList.add("978-3-16-148410-0");
        bibItem.setIsbn(isbnList);
        bibItem.setLccn("sample lccn");
        bibItem.setPublicationPlace("Texas");
        bibItem.setPublisher("McGraw Hill");
        bibItem.setPublicationDate("1998");
        bibItem.setSubject("Physics");
        bibItem.setNotes("Notes");
        bibItem.setOwningInstitution("PUL");
        bibItem.setOwningInstitutionBibId("1");
        bibItem.setMaterialType(ScsbCommonConstants.OTHER);
        List<Holdings> holdings = new ArrayList<>();
        Holdings holding= new Holdings();
        holding.setId("1");
        holding.setHoldingsId(1);
        holdings.add(holding);
        bibItem.setHoldingsList(holdings);
        List<Item> items = new ArrayList<>();
        Item item = new Item();
        item.setItemId(1);
        item.setBarcode("BC234");
        item.setCallNumberSearch("123");
        item.setVolumePartYear("V1");
        item.setCustomerCode("NA");
        item.setAvailability("Available");
        item.setHoldingsIdList(Arrays.asList(1));
        items.add(item);
        bibItem.setItems(items);
        bibItems.add(bibItem);
        return bibItems;
    }

    @Test
    public void modifySearchRequestForCirculationFreeze() {
        SearchRecordsRequest searchRecordsRequest = getSearchRecordsRequest();
        Map<String, String> propertyMap = getCirculationFreezePropertyMap();
        propertyMap.put("PUL", "true");
        Mockito.when(propertyUtil.getPropertyByKeyForAllInstitutions(PropertyKeyConstants.ILS.ILS_ENABLE_CIRCULATION_FREEZE)).thenReturn(propertyMap);
        searchRecordsUtil.modifySearchRequestForCirculationFreeze(searchRecordsRequest);
        assertNotNull(searchRecordsRequest);
        assertFalse(searchRecordsRequest.getOwningInstitutions().contains("PUL"));
    }

    private SearchRecordsRequest getSearchRecordsRequest() {
        SearchRecordsRequest searchRecordsRequest = new SearchRecordsRequest(Arrays.asList("PUL", "CUL", "NYPL", "HL"));
        searchRecordsRequest.setAvailability(Collections.singletonList(ScsbCommonConstants.AVAILABLE));
        return searchRecordsRequest;
    }

    private Map<String, String> getCirculationFreezePropertyMap() {
        Map<String, String> propertyMap = new HashMap<>();
        propertyMap.put("PUL", "false");
        propertyMap.put("CUL", "false");
        propertyMap.put("NYPL", "false");
        propertyMap.put("HL", "false");
        return propertyMap;
    }

}