package org.springframework.data.rest.shell.util;

import org.springframework.hateoas.Link;

public class LinkUtil {

    private LinkUtil() {
    }

    /**
     * Normalizes templated Links
     *
     * @param link the link to normalize
     * @return href with templates removed
     */
    public static String normalize(Link link) {
        if (link.isTemplated()) {
            // replaces templates from the url
            return link.getHref().replaceAll("\\{\\?.*?\\}", "");
        }
        return link.getHref();
    }

}
