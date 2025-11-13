package it.gov.pagopa.fdrxmltojson.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import it.gov.digitpa.schemas._2011.pagamenti.CtDatiSingoliPagamenti;
import it.gov.digitpa.schemas._2011.pagamenti.CtFlussoRiversamento;
import it.gov.digitpa.schemas._2011.pagamenti.CtIdentificativoUnivoco;
import it.gov.digitpa.schemas._2011.pagamenti.CtIdentificativoUnivocoPersonaG;
import it.gov.digitpa.schemas._2011.pagamenti.CtIstitutoMittente;
import it.gov.digitpa.schemas._2011.pagamenti.CtIstitutoRicevente;
import it.gov.digitpa.schemas._2011.pagamenti.StTipoIdentificativoUnivoco;
import it.gov.digitpa.schemas._2011.pagamenti.StTipoIdentificativoUnivocoPersG;
import it.gov.pagopa.pagopa_api.node.nodeforpsp.NodoInviaFlussoRendicontazioneRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.junit.jupiter.api.Test;
import org.openapitools.client.model.AddPaymentRequest;
import org.openapitools.client.model.CreateRequest;
import org.openapitools.client.model.Payment;
import org.openapitools.client.model.PaymentStatusEnum;
import org.openapitools.client.model.SenderTypeEnum;

class FdR3ClientUtilTest {

  private final FdR3ClientUtil fdR3ClientUtil = new FdR3ClientUtil();

  @Test
  void getCreateRequest_shouldPreserveLocalItalianDateTimeWhenTimezoneIsMissing() throws Exception {
    NodoInviaFlussoRendicontazioneRequest nodoRequest = buildNodoRequest();
    CtFlussoRiversamento flusso = buildFlussoWithoutTimezone();

    CreateRequest result = fdR3ClientUtil.getCreateRequest(nodoRequest, flusso);

    assertNotNull(result);
    assertEquals("2026-03-24ABI07156-BBG7Z00012345678", result.getFdr());
    assertEquals(
        OffsetDateTime.parse("2026-03-25T15:59:47+01:00"),
        result.getFdrDate());
    assertEquals("Bonifico SEPA-07156-BBG7Z", result.getRegulation());
    assertEquals(
        OffsetDateTime.parse("2026-03-24T00:00:00+01:00"),
        result.getRegulationDate());
    assertEquals(1L, result.getTotPayments());
    assertEquals(21.60, result.getSumPayments());

    assertNotNull(result.getSender());
    assertEquals(SenderTypeEnum.BIC_CODE, result.getSender().getType());
    assertEquals("ABI07156", result.getSender().getId());
    assertEquals("ABI07156", result.getSender().getPspId());
    assertEquals("BANCA S.C.P.A.", result.getSender().getPspName());
    assertEquals("91135022588", result.getSender().getPspBrokerId());
    assertEquals("91135022588_04", result.getSender().getChannelId());
    assertEquals("password", result.getSender().getPassword());

    assertNotNull(result.getReceiver());
    assertEquals("12344360123", result.getReceiver().getId());
    assertEquals("50044360123", result.getReceiver().getOrganizationId());
    assertEquals("ISTITUTO", result.getReceiver().getOrganizationName());
  }

  @Test
  void getCreateRequest_shouldPreserveExplicitTimezoneWhenPresent() throws Exception {
    NodoInviaFlussoRendicontazioneRequest nodoRequest = buildNodoRequest();
    nodoRequest.setDataOraFlusso(xmlDateTime("2026-03-25T15:59:47Z"));

    CtFlussoRiversamento flusso = buildFlussoWithoutTimezone();

    CreateRequest result = fdR3ClientUtil.getCreateRequest(nodoRequest, flusso);

    assertNotNull(result);
    assertEquals(
        OffsetDateTime.parse("2026-03-25T15:59:47Z"),
        result.getFdrDate());
    assertEquals(
        OffsetDateTime.parse("2026-03-24T00:00:00+01:00"),
        result.getRegulationDate());
  }

  @Test
  void getAddPaymentRequestListChunked_shouldSetPayDateAtStartOfItalianDay() throws Exception {
    CtDatiSingoliPagamenti pagamento = buildPagamento();

    List<AddPaymentRequest> result =
        fdR3ClientUtil.getAddPaymentRequestListChunked(Collections.singletonList(pagamento), 1000);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertNotNull(result.get(0).getPayments());
    assertEquals(1, result.get(0).getPayments().size());

    Payment payment = result.get(0).getPayments().get(0);

    assertEquals(1L, payment.getIndex());
    assertEquals(1L, payment.getIdTransfer());
    assertEquals("01000012347818123", payment.getIuv());
    assertEquals("512635111e155233a64c555h04179123", payment.getIur());
    assertEquals(21.60, payment.getPay());
    assertEquals(
        OffsetDateTime.parse("2026-03-24T00:00:00+01:00"),
        payment.getPayDate());
    assertEquals(PaymentStatusEnum.EXECUTED, payment.getPayStatus());
  }

  @Test
  void getAddPaymentRequestListChunked_shouldPartitionAndIncrementIndex() throws Exception {
    CtDatiSingoliPagamenti pagamento1 = buildPagamento();
    CtDatiSingoliPagamenti pagamento2 = buildPagamento();
    pagamento2.setIdentificativoUnivocoVersamento("01000012347818124");
    pagamento2.setIdentificativoUnivocoRiscossione("512635111e155233a64c555h04179124");

    List<AddPaymentRequest> result =
        fdR3ClientUtil.getAddPaymentRequestListChunked(List.of(pagamento1, pagamento2), 1);

    assertNotNull(result);
    assertEquals(2, result.size());

    Payment firstPayment = result.get(0).getPayments().get(0);
    Payment secondPayment = result.get(1).getPayments().get(0);

    assertEquals(1L, firstPayment.getIndex());
    assertEquals(2L, secondPayment.getIndex());

    assertEquals("01000012347818123", firstPayment.getIuv());
    assertEquals("01000012347818124", secondPayment.getIuv());

    assertEquals(
        OffsetDateTime.parse("2026-03-24T00:00:00+01:00"),
        firstPayment.getPayDate());
    assertEquals(
        OffsetDateTime.parse("2026-03-24T00:00:00+01:00"),
        secondPayment.getPayDate());
  }

  private NodoInviaFlussoRendicontazioneRequest buildNodoRequest() throws Exception {
    NodoInviaFlussoRendicontazioneRequest request = new NodoInviaFlussoRendicontazioneRequest();
    request.setIdentificativoFlusso("2026-03-24ABI07156-BBG7Z00012345678");
    request.setDataOraFlusso(xmlDateTime("2026-03-25T15:59:47"));
    request.setIdentificativoPSP("ABI07156");
    request.setIdentificativoIntermediarioPSP("91135022588");
    request.setIdentificativoCanale("91135022588_04");
    request.setPassword("password");
    request.setIdentificativoDominio("50044360123");
    return request;
  }

  private CtFlussoRiversamento buildFlussoWithoutTimezone() throws Exception {
    CtFlussoRiversamento flusso = new CtFlussoRiversamento();
    flusso.setIdentificativoUnivocoRegolamento("Bonifico SEPA-07156-BBG7Z");
    flusso.setDataRegolamento(xmlDate("2026-03-24"));
    flusso.setNumeroTotalePagamenti(BigDecimal.valueOf(1));
    flusso.setImportoTotalePagamenti(BigDecimal.valueOf(21.60));
    flusso.setIstitutoMittente(buildIstitutoMittente());
    flusso.setIstitutoRicevente(buildIstitutoRicevente());
    flusso.setCodiceBicBancaDiRiversamento("UNCRITMM");
    return flusso;
  }

  private CtIstitutoMittente buildIstitutoMittente() {
    CtIstitutoMittente mittente = new CtIstitutoMittente();
    CtIdentificativoUnivoco id = new CtIdentificativoUnivoco();
    id.setTipoIdentificativoUnivoco(StTipoIdentificativoUnivoco.B);
    id.setCodiceIdentificativoUnivoco("ABI07156");
    mittente.setIdentificativoUnivocoMittente(id);
    mittente.setDenominazioneMittente("BANCA S.C.P.A.");
    return mittente;
  }

  private CtIstitutoRicevente buildIstitutoRicevente() {
    CtIstitutoRicevente ricevente = new CtIstitutoRicevente();
    CtIdentificativoUnivocoPersonaG id = new CtIdentificativoUnivocoPersonaG();
    id.setTipoIdentificativoUnivoco(StTipoIdentificativoUnivocoPersG.G);
    id.setCodiceIdentificativoUnivoco("12344360123");
    ricevente.setIdentificativoUnivocoRicevente(id);
    ricevente.setDenominazioneRicevente("ISTITUTO");
    return ricevente;
  }

  private CtDatiSingoliPagamenti buildPagamento() throws Exception {
    CtDatiSingoliPagamenti pagamento = new CtDatiSingoliPagamenti();
    pagamento.setIndiceDatiSingoloPagamento(1);
    pagamento.setIdentificativoUnivocoVersamento("01000012347818123");
    pagamento.setIdentificativoUnivocoRiscossione("512635111e155233a64c555h04179123");
    pagamento.setSingoloImportoPagato(BigDecimal.valueOf(21.60));
    pagamento.setCodiceEsitoSingoloPagamento("0");
    pagamento.setDataEsitoSingoloPagamento(xmlDate("2026-03-24"));
    return pagamento;
  }

  private XMLGregorianCalendar xmlDateTime(String value) throws Exception {
    return DatatypeFactory.newInstance().newXMLGregorianCalendar(value);
  }

  private XMLGregorianCalendar xmlDate(String value) throws Exception {
    return DatatypeFactory.newInstance().newXMLGregorianCalendar(value);
  }
}