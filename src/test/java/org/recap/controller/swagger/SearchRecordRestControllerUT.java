package org.recap.controller.swagger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.recap.BaseTestCaseUT;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.recap.model.search.DataDumpSearchResult;
import org.recap.model.search.SearchRecordsRequest;
import org.recap.model.search.SearchRecordsResponse;
import org.recap.model.search.SearchResultRow;
import org.recap.util.PropertyUtil;
import org.recap.util.SearchRecordsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Created by premkb on 19/8/16.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest(SearchRecordsUtil.class)
public class SearchRecordRestControllerUT extends BaseTestCaseUT {

    private static final Logger logger = LoggerFactory.getLogger(SearchRecordRestController.class);

    @InjectMocks
    SearchRecordRestController searchRecordRestController;

    @Mock
    PropertyUtil propertyUtil;

    @Value("${" + PropertyKeyConstants.SCSB_SUPPORT_INSTITUTION + "}")
    private String supportInstitution;

    @Test
    public void searchRecordsServiceGetParam() throws Exception {
        SearchRecordsRequest searchRecordsRequest=new SearchRecordsRequest();
        searchRecordsRequest.setFieldName(ScsbCommonConstants.CUSTOMER_CODE);
        searchRecordsRequest.setFieldValue("pa");
        searchRecordsRequest.setErrorMessage("test");
        List<SearchResultRow> searchResultRows=new ArrayList<>();
        SearchResultRow searchResultRow=new SearchResultRow();
        searchResultRows.add(searchResultRow);
        SearchRecordsUtil searchRecordsUtil= PowerMockito.mock(SearchRecordsUtil.class);
        ReflectionTestUtils.setField(searchRecordRestController,"searchRecordsUtil",searchRecordsUtil);
        Mockito.when(searchRecordsUtil.searchRecords(Mockito.any())).thenReturn(searchResultRows);
        SearchRecordsResponse searchRecordsResponse =searchRecordRestController.searchRecordsServiceGetParam(searchRecordsRequest);
        assertEquals("test",searchRecordsResponse.getErrorMessage());
    }

    @Test
    public void searchRecordsServiceGetParamException() throws Exception {
        SearchRecordsRequest searchRecordsRequest=new SearchRecordsRequest();
        SearchRecordsUtil searchRecordsUtil= PowerMockito.mock(SearchRecordsUtil.class);
        ReflectionTestUtils.setField(searchRecordRestController,"searchRecordsUtil",searchRecordsUtil);
        Mockito.when(searchRecordsUtil.searchRecords(Mockito.any())).thenThrow(NullPointerException.class);
        Mockito.when(propertyUtil.getAllInstitutions()).thenReturn(Arrays.asList("PUL","CUL","NYPL","HL", supportInstitution));

        SearchRecordsResponse searchRecordsResponse =searchRecordRestController.searchRecordsServiceGetParam(searchRecordsRequest);
        assertNull(searchRecordsResponse.getErrorMessage());
    }

    @Test
    public void searchRecords() throws Exception {
        SearchRecordsRequest searchRecordsRequest=new SearchRecordsRequest();
        searchRecordsRequest.setTotalPageCount(1);
        List<SearchResultRow> searchResultRows=new ArrayList<>();
        SearchResultRow searchResultRow=new SearchResultRow();
        searchResultRows.add(searchResultRow);
        SearchRecordsUtil searchRecordsUtil= PowerMockito.mock(SearchRecordsUtil.class);
        ReflectionTestUtils.setField(searchRecordRestController,"searchRecordsUtil",searchRecordsUtil);
        List<DataDumpSearchResult> dataDumpSearchResults=new ArrayList<>();
        DataDumpSearchResult dataDumpSearchResult=new DataDumpSearchResult();
        dataDumpSearchResults.add(dataDumpSearchResult);
        Mockito.when(searchRecordsUtil.searchRecordsForDataDump(Mockito.any())).thenReturn(dataDumpSearchResults);
        Map responseMap =searchRecordRestController.searchRecords(searchRecordsRequest);
        assertEquals(1,responseMap.get("totalPageCount"));
    }

    @Test
    public void searchRecordsException() throws Exception {
        SearchRecordsRequest searchRecordsRequest=new SearchRecordsRequest();
        SearchRecordsUtil searchRecordsUtil= PowerMockito.mock(SearchRecordsUtil.class);
        ReflectionTestUtils.setField(searchRecordRestController,"searchRecordsUtil",searchRecordsUtil);
        Mockito.when(searchRecordsUtil.searchRecordsForDataDump(Mockito.any())).thenThrow(NullPointerException.class);
        Mockito.when(propertyUtil.getAllInstitutions()).thenReturn(Arrays.asList("PUL","CUL","NYPL","HL", supportInstitution));
        Map responseMap =searchRecordRestController.searchRecords(searchRecordsRequest);
        assertNotNull(responseMap);
    }

    @Test
    public void searchRecordsServiceGetException() throws Exception {
        SearchRecordsUtil searchRecordsUtil= PowerMockito.mock(SearchRecordsUtil.class);
        ReflectionTestUtils.setField(searchRecordRestController,"searchRecordsUtil",searchRecordsUtil);
        Mockito.when(searchRecordsUtil.searchRecords(Mockito.any())).thenThrow(NegativeArraySizeException.class);
        Mockito.when(propertyUtil.getAllInstitutions()).thenReturn(Arrays.asList("PUL","CUL","NYPL","HL", supportInstitution));
        List<SearchResultRow> responseMap =searchRecordRestController.searchRecordsServiceGet("test","test","PUL,CUL,NYPL","Shared,Open,Private","Available,Notavailable","Monograph,Serial,Other","test",1);
        assertNotNull(responseMap);
    }

    @Test
    public void searchRecordsServiceGet() throws Exception {
        SearchRecordsUtil searchRecordsUtil= PowerMockito.mock(SearchRecordsUtil.class);
        ReflectionTestUtils.setField(searchRecordRestController,"searchRecordsUtil",searchRecordsUtil);
        List<SearchResultRow> searchResultRows=new ArrayList<>();
        SearchResultRow searchResultRow=new SearchResultRow();
        searchResultRows.add(searchResultRow);
        Mockito.when(searchRecordsUtil.searchRecords(Mockito.any())).thenReturn(searchResultRows);
        List<SearchResultRow> responseMap =searchRecordRestController.searchRecordsServiceGet("test","test","PUL,CUL,NYPL","Shared,Open,Private","Available,Notavailable","Monograph,Serial,Other","test",1);
        assertEquals(searchResultRows,responseMap);
    }


}
