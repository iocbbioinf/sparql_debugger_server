package cz.iocb.idsm.debugger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task that cleans up old SPARQL queries.
 * It runs daily at midnight and deletes queries that have exceeded
 * their expiration time as defined by the configuration property.
 */
@Component
@EnableScheduling
public class QueryCleanerScheduledTask {

    @Value("${cz.iocb.idsm.debugger.service.queryExpInHours}")
    private int queryExpInHours;

    @Autowired
    private SparqlEndpointService endpointService;

    private static final Logger logger = LoggerFactory.getLogger(QueryCleanerScheduledTask.class);

    /**
     * Deletes old queries based on the expiration time.
     */
    @Scheduled(cron = "0 0 0 * * ?") // Run every day at midnight
    public void deleteOldQueries() {

        logger.info("deleteOldQueries begins");

        long currentTime = System.currentTimeMillis();
        long cutoff = currentTime - queryExpInHours * 60 * 60 * 1000;

        endpointService.deleteQueries(cutoff);

        logger.info("deleteOldQueries ends");
    }

}
