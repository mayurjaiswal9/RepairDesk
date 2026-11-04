package com.acetelecom.repairdesk.feature.company;

import java.util.UUID;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;

/**
 * Company of the request currently on this thread. Empty for schedulers and for
 * requests that have not signed in. A missing value does not mean "all companies"
 * for a row that already has an id: callers that loaded a row by primary key
 * still pass that id to {@link #requireSame}.
 */
public final class CompanyContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private CompanyContext() {
    }

    public static void set(UUID companyId) {
        if (companyId == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(companyId);
        }
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Same not-found message as a missing row, so the caller cannot learn the id exists. */
    public static void requireSame(UUID rowCompanyId, String notFoundMessage) {
        UUID current = CURRENT.get();
        if (current == null || rowCompanyId == null) {
            return;
        }
        if (!current.equals(rowCompanyId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, notFoundMessage);
        }
    }
}
