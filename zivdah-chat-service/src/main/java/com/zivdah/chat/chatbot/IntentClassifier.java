package com.zivdah.chat.chatbot;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Lower-case + keyword/regex matching (no ML) — one {@link Intent} per spec'd question type.
 * {@code TALK_TO_HUMAN} is checked first and short-circuits everything else, per the plan.
 *
 * <p>Before matching, {@link #correctTypos(String)} nudges near-miss tokens (edit distance 1-2,
 * scaled by word length) back onto a small set of known keywords — still no ML, just a
 * deterministic string-distance pass, so a message like "cancle oder 1042" still resolves to
 * CANCEL_ORDER instead of falling through to the generic fallback reply.
 */
@Component
public class IntentClassifier {

    public enum Intent {
        ORDER_STATUS,
        CANCEL_ORDER,
        REFUND_REQUEST,
        ORDER_HISTORY,
        PRODUCT_SEARCH,
        OFFERS,
        DELIVERY_TIME,
        CHANGE_ADDRESS,
        TALK_TO_HUMAN,
        UNKNOWN
    }

    // Checked first — short-circuits every other intent, per the plan.
    private static final Pattern TALK_TO_HUMAN_PATTERN =
            Pattern.compile("\\b(human|agent|representative|real person|talk to (a )?person|customer care|support person)\\b");

    private static final Pattern REFUND_PATTERN =
            Pattern.compile("\\b(refund|money back|reimburse(ment)?|return (my )?(money|payment))\\b");

    // Bare "cancel" (no accompanying "order") falls through to this same intent too — in a
    // grocery-order chatbot, an unqualified "cancel" overwhelmingly means "cancel my order".
    private static final Pattern CANCEL_ORDER_PATTERN =
            Pattern.compile("\\bcancel\\b.*\\border\\b|\\border\\b.*\\bcancel\\b|cancel my order|\\bcancel\\b");

    // Bare "orders" (plural) / "history" join the existing phrase matches — kept distinct from
    // ORDER_STATUS_PATTERN's bare "order" (singular) below via exact \b word boundaries, so
    // "order" vs "orders" cleanly pick different intents instead of colliding.
    private static final Pattern ORDER_HISTORY_PATTERN =
            Pattern.compile("\\b(order history|my orders|past orders|previous orders|all my orders|order list|orders|history)\\b");

    // Bare "order" (singular) added — checked after CANCEL_ORDER/ORDER_HISTORY above, so a plain
    // "order" not paired with "cancel" or pluralized still lands here as the sensible default.
    private static final Pattern ORDER_STATUS_PATTERN =
            Pattern.compile("\\b(order status|track (my )?order|where is my order|where'?s my order|my order|order #?\\d+|order)\\b");

    // Bare "product"/"products" added alongside the existing search-phrase matches.
    private static final Pattern PRODUCT_SEARCH_PATTERN =
            Pattern.compile("\\b(looking for|search for|do you have|show me|find me|any (.+ )?available|products?)\\b");

    // Already matched bare "offer(s)" via the trailing `s?` — no change needed here.
    private static final Pattern OFFERS_PATTERN =
            Pattern.compile("\\b(offer|discount|coupon|deal|promo(tion)?|sale)s?\\b");

    // Bare "delivery" added — checked before CHANGE_ADDRESS, so "delivery address" (no verb)
    // resolves here rather than falling through to UNKNOWN; an address-change verb still wins
    // CHANGE_ADDRESS explicitly (e.g. "update delivery address").
    private static final Pattern DELIVERY_TIME_PATTERN =
            Pattern.compile("\\b(delivery time|when will (it|my order) arrive|how long (will|does) (it|delivery) take|eta|estimated delivery|delivery slot|delivery)\\b");

    // Bare "address" added alongside the existing verb+address phrase match.
    private static final Pattern CHANGE_ADDRESS_PATTERN =
            Pattern.compile("\\b(change|update|edit|add) (my )?(delivery )?address\\b|\\baddress\\b");

    // Short standalone replies to a yes/no prompt (e.g. a cancel confirmation) — anchored
    // whole-message matches via isAffirmative/isNegative, deliberately NOT part of the
    // intent-pattern matching above (a sentence merely containing "no" shouldn't hijack it).
    private static final Pattern AFFIRMATIVE_PATTERN =
            Pattern.compile("^(yes|yeah|yep|yup|sure|confirm(ed)?|ok(ay)?|do it|go ahead|please( do it)?|correct)[.!]?$");
    private static final Pattern NEGATIVE_PATTERN =
            Pattern.compile("^(no|nah|nope|don'?t|do not|never ?mind|keep it|keep( the)? order|leave it)[.!]?$");

    // Anchor keywords for correctTypos — every trigger word above with a length worth
    // typo-correcting (short words like "no"/"eta" are excluded: too easy to false-positive on).
    private static final List<String> CORRECTABLE_KEYWORDS = List.of(
            "cancel", "order", "orders", "refund", "delivery", "address",
            "product", "products", "offer", "offers", "human", "agent",
            "status", "track", "history", "discount", "coupon");

    public Intent classify(String message) {
        if (message == null || message.isBlank()) {
            return Intent.UNKNOWN;
        }
        String text = correctTypos(message.toLowerCase().trim());

        if (TALK_TO_HUMAN_PATTERN.matcher(text).find()) {
            return Intent.TALK_TO_HUMAN;
        }
        if (REFUND_PATTERN.matcher(text).find()) {
            return Intent.REFUND_REQUEST;
        }
        if (CANCEL_ORDER_PATTERN.matcher(text).find()) {
            return Intent.CANCEL_ORDER;
        }
        if (ORDER_HISTORY_PATTERN.matcher(text).find()) {
            return Intent.ORDER_HISTORY;
        }
        if (ORDER_STATUS_PATTERN.matcher(text).find()) {
            return Intent.ORDER_STATUS;
        }
        if (DELIVERY_TIME_PATTERN.matcher(text).find()) {
            return Intent.DELIVERY_TIME;
        }
        if (CHANGE_ADDRESS_PATTERN.matcher(text).find()) {
            return Intent.CHANGE_ADDRESS;
        }
        if (OFFERS_PATTERN.matcher(text).find()) {
            return Intent.OFFERS;
        }
        if (PRODUCT_SEARCH_PATTERN.matcher(text).find()) {
            return Intent.PRODUCT_SEARCH;
        }
        return Intent.UNKNOWN;
    }

    // Standalone "yes"/"no"-shaped replies — used by ChatbotServiceImpl to interpret a typed
    // reply to a pending confirmation prompt, as an alternative to clicking the quick-reply
    // buttons. Deliberately whole-message (not classify()'d), so these never fire mid-sentence.
    public boolean isAffirmative(String message) {
        return message != null && AFFIRMATIVE_PATTERN.matcher(message.toLowerCase().trim()).matches();
    }

    public boolean isNegative(String message) {
        return message != null && NEGATIVE_PATTERN.matcher(message.toLowerCase().trim()).matches();
    }

    // Token-by-token nudge toward the nearest CORRECTABLE_KEYWORDS entry when within a small
    // edit-distance budget (tighter for short words, since e.g. "oder"/"odor" are both distance-1
    // from "order" but only one is a plausible typo threshold-wise for a 5-letter word). Leaves
    // already-correct/unrelated words untouched — this only ever tightens a match, never a whole
    // new false trigger, since the corrected word still has to satisfy one of the intent patterns.
    private String correctTypos(String text) {
        String[] tokens = text.split("\\s+");
        StringBuilder corrected = new StringBuilder(text.length());
        for (String token : tokens) {
            String word = token.replaceAll("[^a-z]", "");
            String bestMatch = null;
            if (word.length() >= 4 && !CORRECTABLE_KEYWORDS.contains(word)) {
                int bestDistance = Integer.MAX_VALUE;
                int threshold = word.length() <= 5 ? 1 : 2;
                for (String keyword : CORRECTABLE_KEYWORDS) {
                    int distance = levenshteinDistance(word, keyword);
                    if (distance <= threshold && distance < bestDistance) {
                        bestDistance = distance;
                        bestMatch = keyword;
                    }
                }
            }
            if (corrected.length() > 0) {
                corrected.append(' ');
            }
            corrected.append(bestMatch != null ? bestMatch : token);
        }
        return corrected.toString();
    }

    private int levenshteinDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            System.arraycopy(curr, 0, prev, 0, curr.length);
        }
        return prev[b.length()];
    }
}
