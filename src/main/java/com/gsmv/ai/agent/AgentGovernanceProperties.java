package com.gsmv.ai.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gsmv.ai.agent.knowledge-governance")
public class AgentGovernanceProperties {

    private boolean enabled = true;
    private String cron = "0 30 2 * * *";
    private String zone = "Asia/Shanghai";
    private int scanLimit = 200;
    private int maxIssueDocuments = 20;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public int getScanLimit() {
        return scanLimit;
    }

    public void setScanLimit(int scanLimit) {
        this.scanLimit = scanLimit;
    }

    public int getMaxIssueDocuments() {
        return maxIssueDocuments;
    }

    public void setMaxIssueDocuments(int maxIssueDocuments) {
        this.maxIssueDocuments = maxIssueDocuments;
    }
}
