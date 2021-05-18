package org.recap.repository.solr.temp;

import org.junit.Test;
import org.mockito.Mock;
import org.recap.BaseTestCaseUT;
import org.recap.PropertyKeyConstants;
import org.recap.model.solr.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.solr.core.SolrTemplate;

import static junit.framework.TestCase.assertNotNull;

/**
 * Created by premkb on 1/8/16.
 */

public class ItemCrudRepositoryMultiCoreSupportUT extends BaseTestCaseUT {

    @Value("${" + PropertyKeyConstants.SOLR_URL + "}")
    String solrUrl;

    @Mock
    SolrTemplate solrTemplate;

    @Test
    public void instantiateItemCrudRepositoryMultiCoreSupport(){
        assertNotNull(new ItemCrudRepositoryMultiCoreSupport(solrTemplate, Item.class));
    }
}
