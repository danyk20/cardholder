# 8. Reading bank cards over NFC

- Status: accepted
- Date: 2026-10-06

## Context
Typing a 16-digit card number is error-prone. Contactless (EMV) cards expose the card number and expiry
to any reader, the same data a payment terminal reads before a transaction.

## Decision
- A small, unit-tested EMV reader (`EmvCardReader`, BER-TLV parser) implements only the read-only part of
  the contactless flow: SELECT the payment directory (PPSE) or known network AIDs → SELECT application →
  GET PROCESSING OPTIONS (with the card's PDOL filled with neutral terminal values) → READ RECORD for the
  AFL entries. No transaction is started, nothing is written to the card.
- Number and expiry come from tags `5A`/`5F24` or the track-2 equivalent `57`. The holder name (`5F20`/`9F0B`)
  is used only when the card provides one; most modern cards withhold it.
- The **CVV is never on the chip**, so it always has to be typed.
- Android reader mode is enabled only while the read dialog is shown; read data stays in memory and goes
  straight into the form. The dialog is `FLAG_SECURE`.
- Own implementation instead of a third-party EMV library: the needed subset is small, testable without
  hardware, and avoids an unmaintained dependency in a security-sensitive path.

## Consequences
Works with Visa, Mastercard/Maestro, Amex, Discover, JCB and UnionPay cards that support contactless.
Phones without NFC don't show the option. Behaviour on real cards can only be verified on a device.
