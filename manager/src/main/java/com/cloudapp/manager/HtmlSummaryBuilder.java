package com.cloudapp.manager;

import com.cloudapp.common.WorkerResultMessage;
import java.util.Collection;

public class HtmlSummaryBuilder {

    /**
     * Builds an HTML summary page from the given Worker results.:
     * <analysis type>: <input file> <output file (link) / error>
     */
    public String buildHtml(Collection<WorkerResultMessage> results, String bucketName) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html><head><meta charset=\"UTF-8\"><title>Analysis Summary</title></head><body>\n");
        sb.append("<h1>Text Analysis Summary</h1>\n");
        sb.append("<ul>\n");

        for (WorkerResultMessage r : results) {
            sb.append("<li>");
            sb.append(escapeHtml(r.getAnalysisType())).append(": ");

            // input link 
            String inputUrl = r.getUrl();
            sb.append("<a href=\"").append(escapeHtml(inputUrl)).append("\">")
              .append(escapeHtml(inputUrl))
              .append("</a> ");

            if (r.getError() != null) {
                sb.append(escapeHtml(r.getError()));
            } else if (r.getOutputS3Key() != null) {
                // link to output file in S3 
                String s3Url = "https://" + bucketName + ".s3.amazonaws.com/" + r.getOutputS3Key();
                sb.append("<a href=\"").append(escapeHtml(s3Url)).append("\">")
                  .append(escapeHtml(r.getOutputS3Key()))
                  .append("</a>");
            } else {
                sb.append("No output available");
            }

            sb.append("</li>\n");
        }

        sb.append("</ul>\n");
        sb.append("</body></html>\n");
        return sb.toString();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
