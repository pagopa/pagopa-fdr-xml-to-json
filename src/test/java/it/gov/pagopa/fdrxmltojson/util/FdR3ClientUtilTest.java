package it.gov.pagopa.fdrxmltojson.util;

import it.gov.digitpa.schemas._2011.pagamenti.CtDatiSingoliPagamenti;
import it.gov.digitpa.schemas._2011.pagamenti.CtFlussoRiversamento;
import it.gov.pagopa.pagopa_api.node.nodeforpsp.NodoInviaFlussoRendicontazioneRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openapitools.client.model.AddPaymentRequest;
import org.openapitools.client.model.CreateRequest;
import org.openapitools.client.model.Payment;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests focused on date/time normalization introduced to prevent:
 * - fdrDate shifting due to withOffsetSameLocal(UTC)
 * - payDate/regulationDate drifting due to system timezone / DST
 */
class FdR3ClientUtilTest {

  private TimeZone previousTz;

  @BeforeEach
  void setUp() {
    previousTz = TimeZone.getDefault();
    // Use a TZ with DST to reproduce the same kind of shifts reported in the PIDM-1313 ticket.
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"));
  }

  @AfterEach
  void tearDown() {
    TimeZone.setDefault(previousTz);
  }

  @Test
  void getCreateRequest_shouldNormalizeFdrDateToUtcPreservingInstant() throws Exception {
    // Given: dataOraFlusso in the test XML is "2024-05-21T01:00:59" (no timezone)
    // With system TZ Europe/Rome on that date (DST, UTC+2), the expected UTC instant is 2024-05-20T23:00:59Z.
    Parsed parsed = parseFromResource("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    FdR3ClientUtil util = new FdR3ClientUtil();
    CreateRequest req = util.getCreateRequest(parsed.nodoReq, parsed.flusso);

    assertNotNull(req.getFdrDate(), "fdrDate must be set");
    assertEquals(OffsetDateTime.parse("2024-05-20T23:00:59Z"), req.getFdrDate(),
        "fdrDate must be converted to UTC preserving the same instant (regression guard vs withOffsetSameLocal)");
  }

  @Test
  void getCreateRequest_shouldNormalizeRegulationDateToUtcMidnight() throws Exception {
    // Given: dataRegolamento in the test XML is an xs:date "2024-05-21"
    Parsed parsed = parseFromResource("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    FdR3ClientUtil util = new FdR3ClientUtil();
    CreateRequest req = util.getCreateRequest(parsed.nodoReq, parsed.flusso);

    assertNotNull(req.getRegulationDate(), "regulationDate must be set");
    assertEquals(OffsetDateTime.parse("2024-05-21T00:00:00Z"), req.getRegulationDate(),
        "regulationDate must be normalized to 00:00Z to avoid timezone-dependent day shifts");
  }

  @Test
  void getAddPaymentRequestListChunked_shouldNormalizePayDateToUtcMidnight_andKeepIdTransferSeparateFromIndex()
      throws Exception {
    // Given: the XML contains 5 payments, each with indiceDatiSingoloPagamento = 1 and dataEsito = 2025-03-18
    Parsed parsed = parseFromResource("xmlcontent/nodoInviaFlussoRendicontazione_morePayments.xml");
    List<CtDatiSingoliPagamenti> payments = parsed.flusso.getDatiSingoliPagamenti();
    assertEquals(5, payments.size(), "Test resource should contain 5 payments");

    FdR3ClientUtil util = new FdR3ClientUtil();
    List<AddPaymentRequest> chunks = util.getAddPaymentRequestListChunked(payments, 2);

    // 5 payments chunked by 2 -> 3 chunks (2,2,1)
    assertEquals(3, chunks.size(), "Chunking must split the list as expected");

    List<Payment> out = chunks.stream()
        .flatMap(c -> c.getPayments().stream())
        .toList();
    assertEquals(5, out.size(), "All payments must be preserved");

    // Index must be sequential across chunks
    for (int i = 0; i < out.size(); i++) {
      assertEquals(i + 1L, out.get(i).getIndex(), "Payment.index must be a progressive counter across chunks");
    }

    // idTransfer must remain what comes from XML (1..5), not the progressive index
    out.forEach(p -> assertEquals(1L, p.getIdTransfer(),
        "Payment.idTransfer must be mapped from indiceDatiSingoloPagamento and must not be confused with Payment.index"));

    // payDate is an xs:date -> normalize to 00:00Z (stable reference)
    out.forEach(p -> assertEquals(OffsetDateTime.parse("2025-03-18T00:00:00Z"), p.getPayDate(),
        "payDate must be normalized to 00:00Z to avoid timezone/DST drift"));
  }

  @Test
  void payDateNormalization_shouldBeStableEvenIfSystemTimezoneChanges() throws Exception {
    Parsed parsed = parseFromResource("xmlcontent/nodoInviaFlussoRendicontazione.xml");
    CtDatiSingoliPagamenti pago = parsed.flusso.getDatiSingoliPagamenti().get(0);

    FdR3ClientUtil util = new FdR3ClientUtil();

    // Convert with Europe/Rome
    List<AddPaymentRequest> rome = util.getAddPaymentRequestListChunked(List.of(pago), 1000);
    OffsetDateTime payDateRome = rome.get(0).getPayments().get(0).getPayDate();

    // Change TZ and convert again
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    List<AddPaymentRequest> utc = util.getAddPaymentRequestListChunked(List.of(pago), 1000);
    OffsetDateTime payDateUtc = utc.get(0).getPayments().get(0).getPayDate();

    assertEquals(OffsetDateTime.parse("2017-07-17T00:00:00Z"), payDateRome,
        "Sanity check on expected normalized payDate");
    assertEquals(payDateRome, payDateUtc,
        "payDate normalization must not depend on system default timezone");
  }
  
  @Test
  void getCreateRequest_shouldNotShiftInstant_regressionGuard_againstWithOffsetSameLocal() throws Exception {
    // Given
    Parsed parsed = parseFromResource("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    // When: current implementation (correct) via FdR3ClientUtil
    FdR3ClientUtil util = new FdR3ClientUtil();
    CreateRequest req = util.getCreateRequest(parsed.nodoReq, parsed.flusso);
    OffsetDateTime actual = req.getFdrDate();

    // And: simulate previous buggy behaviour (withOffsetSameLocal(UTC))
    // This keeps the local time but changes the offset, therefore changing the instant.
    OffsetDateTime buggy = parsed.nodoReq.getDataOraFlusso()
        .toGregorianCalendar()
        .toZonedDateTime()
        .toOffsetDateTime()
        .withOffsetSameLocal(ZoneOffset.UTC);

    // Then: the correct result must be what we expect (preserve instant)
    assertEquals(OffsetDateTime.parse("2024-05-20T23:00:59Z"), actual,
        "Correct behaviour: normalize to UTC preserving the same instant");

    // And: the buggy result must NOT match (it would shift the instant)
    assertNotEquals(actual, buggy,
        "Buggy behaviour (withOffsetSameLocal) must shift the instant and therefore differ from the correct UTC value");
  }


  private static Parsed parseFromResource(String resource) throws Exception {
    String xml = TestUtil.readStringFromFile(resource);
    XMLParser parser = new XMLParser();
    NodoInviaFlussoRendicontazioneRequest nodo =
        parser.getInstanceByStAX(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
            NodoInviaFlussoRendicontazioneRequest.class);
    CtFlussoRiversamento flusso =
        parser.getInstanceByBytes(nodo.getXmlRendicontazione(), CtFlussoRiversamento.class);
    return new Parsed(nodo, flusso);
  }

  private static final class Parsed {
    final NodoInviaFlussoRendicontazioneRequest nodoReq;
    final CtFlussoRiversamento flusso;

    private Parsed(NodoInviaFlussoRendicontazioneRequest nodoReq, CtFlussoRiversamento flusso) {
      this.nodoReq = nodoReq;
      this.flusso = flusso;
    }
  }
}