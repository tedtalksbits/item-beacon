package dev.whizzle.itembeacon.client;

import dev.whizzle.itembeacon.model.Protocol;
import dev.whizzle.itembeacon.model.Protocol.SummaryItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Collects the pages of a summary (docs/PROTOCOL.md). A page of a new session starts over (an unfinished older one
 * is dropped). Pure Java.
 */
public final class SummaryAssembler {
    public record Summary(int session, Protocol.Beacon beacon, List<SummaryItem> items) {}

    private int session = -1;
    private List<List<SummaryItem>> pages = List.of();
    private int received;

    /** Takes a page; returns the whole summary once its last missing page arrived, else null. */
    public Summary accept(Protocol.SummaryPage page) {
        if (page.session() != session || page.pages() != pages.size()) {
            session = page.session();
            pages = new ArrayList<>(page.pages());
            for (int i = 0; i < page.pages(); i++) pages.add(null);
            received = 0;
        }
        if (pages.get(page.page()) == null) received++;
        pages.set(page.page(), page.items());
        if (received < pages.size()) return null;
        List<SummaryItem> all = new ArrayList<>();
        for (List<SummaryItem> p : pages) all.addAll(p);
        all.sort(Comparator.comparingInt(SummaryItem::id));
        Summary done = new Summary(session, page.beacon(), all);
        session = -1;
        pages = List.of();
        received = 0;
        return done;
    }
}
