package org.recap.camel.route;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.BindyType;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbConstants;
import org.recap.model.csv.OngoingAccessionReportRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Created by hemalathas on 20/12/16.
 */
@Component
public class FSOngoingAccessionReportRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(FSOngoingAccessionReportRouteBuilder.class);

    /**
     * Instantiates a new route builder to generate ongoing accession report to the file system.
     *
     * @param context          the context
     * @param reportsDirectory the reports directory
     */
    @Autowired
    public FSOngoingAccessionReportRouteBuilder(CamelContext context, @Value("${" + PropertyKeyConstants.ONGOING_ACCESSION_COLLECTION_REPORT_DIRECTORY + "}") String reportsDirectory) {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from(ScsbConstants.FS_ONGOING_ACCESSION_REPORT_Q)
                            .routeId(ScsbConstants.FS_ONGOING_ACCESSION_REPORT_ID)
                            .marshal().bindy(BindyType.Csv, OngoingAccessionReportRecord.class)
                            .to("file:" + reportsDirectory + File.separator + "?fileName=${in.header.fileName}-${date:now:ddMMMyyyyHHmmss}.csv&fileExist=append");
                }
            });
        } catch (Exception e) {
            logger.error(ScsbConstants.ERROR,e);
        }
    }
}
