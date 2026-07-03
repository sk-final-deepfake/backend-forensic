package com.example.demo.util;

import com.example.demo.domain.enums.OrgType;
import java.util.Locale;

public final class OrganizationIdResolver {

    private OrganizationIdResolver() {
    }

    public static String resolve(OrgType organizationType) {
        if (organizationType == null) {
            return "org-unknown";
        }
        return "org-" + organizationType.name().toLowerCase(Locale.ROOT);
    }
}
