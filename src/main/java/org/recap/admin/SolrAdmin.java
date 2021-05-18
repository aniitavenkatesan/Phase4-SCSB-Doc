package org.recap.admin;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.common.params.CoreAdminParams;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by pvsubrah on 6/12/16.
 */
@Component
public class SolrAdmin {

    private static final Logger logger = LoggerFactory.getLogger(SolrAdmin.class);

    @Value("${" + PropertyKeyConstants.SOLR_CONFIGSETS_DIR + "}")
    private String configSetsDir;

    @Value("${" + PropertyKeyConstants.SOLR_SOLR_HOME + "}")
    private String solrHome;

    @Value("${" + PropertyKeyConstants.SOLR_PARENT_CORE + "}")
    private String solrParentCore;

    @Autowired
    private SolrClient solrAdminClient;

    @Autowired
    private SolrClient solrClient;

    private CoreAdminRequest coreAdminRequest;

    /**
     * This method creates solr cores in solr.
     *
     * @param coreNames the core names
     * @return the core admin response
     */
    public CoreAdminResponse createSolrCores(List<String> coreNames) {
        CoreAdminRequest.Create coreAdminRequestCreate = getCoreAdminCreateRequest();
        CoreAdminResponse coreAdminResponse = null;

        for (Iterator<String> iterator = coreNames.iterator(); iterator.hasNext(); ) {
            String coreName = iterator.next();
            String dataDir = solrHome + coreName + File.separator + "data";

            coreAdminRequestCreate.setCoreName(coreName);
            coreAdminRequestCreate.setConfigSet("recap_config");
            coreAdminRequestCreate.setInstanceDir(solrHome + File.separator + coreName);
            coreAdminRequestCreate.setDataDir(dataDir);

            try {
                if (!isCoreExist(coreName)) {
                    coreAdminResponse = coreAdminRequestCreate.process(solrAdminClient);
                    if (coreAdminResponse.getStatus() == 0) {
                        logger.info("Created Solr core with name: {}",coreName);
                    } else {
                        logger.error("Error in creating Solr core with name: {}",coreName);
                    }
                } else {
                    logger.info("Solr core with name {} already exists.",coreName);
                }
            } catch (SolrServerException | IOException e) {
                logger.error(ScsbCommonConstants.LOG_ERROR,e);
            }
        }

        return coreAdminResponse;
    }

    /**
     * This method is used to merge solr cores into the main core.
     *
     * @param coreNames the core names
     */
    public void mergeCores(List<String> coreNames) {
        List<String> tempCores = new ArrayList<>();
        List<String> tempCoreNames = new ArrayList<>();

        for (Iterator<String> iterator = coreNames.iterator(); iterator.hasNext(); ) {
            String coreName = iterator.next();
            tempCores.add(solrHome + File.separator + coreName + File.separator + "data" + File.separator + "index");
            tempCoreNames.add(coreName);
        }

        String[] indexDirs = tempCores.toArray(new String[tempCores.size()]);
        String[] tempCoreNamesObjectArray = tempCoreNames.toArray(new String[tempCores.size()]);
        try {
            CoreAdminRequest.mergeIndexes(solrParentCore, indexDirs, tempCoreNamesObjectArray, solrAdminClient);
            solrClient.commit(solrParentCore);
        } catch (SolrServerException | IOException e) {
            logger.error(ScsbCommonConstants.LOG_ERROR,e);
        }
    }

    /**
     * This method is used to unload solr cores.
     *
     * @param coreNames the core names
     */
    public void unLoadCores(List<String> coreNames){
        for (String coreName : coreNames) {
            try {
                CoreAdminRequest.unloadCore(coreName, true, true, solrAdminClient);
            } catch (SolrServerException | IOException e) {
                logger.error(ScsbCommonConstants.LOG_ERROR, e);
            }

        }
    }


    /**
     * This method unloads temporary solr cores.
     *
     * @throws IOException         the io exception
     * @throws SolrServerException the solr server exception
     */
    public void unloadTempCores() throws IOException, SolrServerException {
        CoreAdminRequest coreAdminRequestToUnload = getCoreAdminRequest();

        coreAdminRequestToUnload.setAction(CoreAdminParams.CoreAdminAction.STATUS);
        CoreAdminResponse cores = coreAdminRequestToUnload.process(solrAdminClient);

        List<String> coreList = new ArrayList<>();
        for (int i = 0; i < cores.getCoreStatus().size(); i++) {
            String name = cores.getCoreStatus().getName(i);
            if (name.contains("temp")) {
                coreList.add(name);
            }
        }

        unLoadCores(coreList);
    }

    /**
     *  This method instantiates the core admin request create object which is used to create solr core.
     *
     * @return the core admin create request
     */
    public CoreAdminRequest.Create getCoreAdminCreateRequest() {
        return new CoreAdminRequest.Create();
    }

    /**
     * This method instantiates the core admin request unload object which is used to remove solr core.
     *
     * @return the core admin unload request
     */
    public CoreAdminRequest.Unload getCoreAdminUnloadRequest() {
        return new CoreAdminRequest.Unload(true);
    }

    /**
     * This method instantiates the core admin request object which can be used to perform operations on solr cores.
     *
     * @return the core admin request
     */
    public CoreAdminRequest getCoreAdminRequest() {
        if (null == coreAdminRequest) {
            coreAdminRequest = new CoreAdminRequest();
        }
        return coreAdminRequest;
    }

    /**
     * Thia method is used to check whether the core exists or not.
     *
     * @param coreName the core name
     * @return the boolean
     * @throws IOException         the io exception
     * @throws SolrServerException the solr server exception
     */
    public boolean isCoreExist(String coreName) throws IOException, SolrServerException {
        CoreAdminRequest coreAdminRequestCheckCore = getCoreAdminRequest();
        coreAdminRequestCheckCore.setAction(CoreAdminParams.CoreAdminAction.STATUS);
        CoreAdminResponse cores = coreAdminRequestCheckCore.process(solrAdminClient);
        for (int i = 0; i < cores.getCoreStatus().size(); i++) {
            String name = cores.getCoreStatus().getName(i);
            if (name.equals(coreName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets cores status to check whether index has happened or not.
     *
     * @return the cores status
     */
    public Integer getCoresStatus() {
        CoreAdminRequest coreAdminRequestCoreStatus = getCoreAdminCreateRequest();
        coreAdminRequestCoreStatus.setAction(CoreAdminParams.CoreAdminAction.STATUS);
        try {
            CoreAdminResponse coresStatusResponse = coreAdminRequestCoreStatus.process(solrAdminClient);
            return coresStatusResponse.getStatus();
        } catch (SolrServerException | IOException e) {
            logger.error(ScsbCommonConstants.LOG_ERROR,e);
        }
        return null;
    }
}
