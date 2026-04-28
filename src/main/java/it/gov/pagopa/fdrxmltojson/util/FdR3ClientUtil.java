package it.gov.pagopa.fdrxmltojson.util;

import com.google.common.collect.Lists;
import it.gov.digitpa.schemas._2011.pagamenti.CtDatiSingoliPagamenti;
import it.gov.digitpa.schemas._2011.pagamenti.CtFlussoRiversamento;
import it.gov.digitpa.schemas._2011.pagamenti.StTipoIdentificativoUnivoco;
import it.gov.pagopa.pagopa_api.node.nodeforpsp.NodoInviaFlussoRendicontazioneRequest;
import org.openapitools.client.ApiClient;
import org.openapitools.client.api.InternalPspApi;
import org.openapitools.client.model.*;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class FdR3ClientUtil {

    private static InternalPspApi pspApi = null;

    private static final String FDR3_INTERNAL_API = System.getenv("FDR_NEW_BASE_URL");
    private static final String FDR3_INTERNAL_API_KEY = System.getenv("FDR_NEW_API_KEY");

    private static final Map<StTipoIdentificativoUnivoco, SenderTypeEnum> typeMap = new LinkedHashMap<>();
    private static final Map<String, PaymentStatusEnum> payStatusMap = new LinkedHashMap<>();
    
    // Italian local timezone used to interpret FDR1 values without explicit timezone
    private static final ZoneId ITALY_ZONE = ZoneId.of("Europe/Rome");
    // Target timezone used to normalize true datetime values before saving them on FDR3
    private static final ZoneId UTC_ZONE = ZoneId.of("UTC");

    static {
        typeMap.put(StTipoIdentificativoUnivoco.G, SenderTypeEnum.LEGAL_PERSON);
        typeMap.put(StTipoIdentificativoUnivoco.A, SenderTypeEnum.ABI_CODE);
        typeMap.put(StTipoIdentificativoUnivoco.B, SenderTypeEnum.BIC_CODE);

        payStatusMap.put("0", PaymentStatusEnum.EXECUTED);
        payStatusMap.put("3", PaymentStatusEnum.REVOKED);
        payStatusMap.put("4", PaymentStatusEnum.STAND_IN);
        payStatusMap.put("8", PaymentStatusEnum.STAND_IN_NO_RPT);
        payStatusMap.put("9", PaymentStatusEnum.NO_RPT);
    }

    public static InternalPspApi getPspApi() {
        if(pspApi == null) {
            ApiClient apiClient = new ApiClient();
            apiClient.setApiKey(FDR3_INTERNAL_API_KEY);
            pspApi = new InternalPspApi(apiClient);
            pspApi.setCustomBaseUrl(FDR3_INTERNAL_API);
        }
        return pspApi;
    }

    public CreateRequest getCreateRequest(NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest, CtFlussoRiversamento ctFlussoRiversamento){
        CreateRequest createRequest = new CreateRequest();
        createRequest.setFdr(nodoInviaFlussoRendicontazioneRequest.getIdentificativoFlusso());
        createRequest.setFdrDate(toUtcOffsetDateTime(nodoInviaFlussoRendicontazioneRequest.getDataOraFlusso()));
        createRequest.setSender(getSender(nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento));
        createRequest.setReceiver(getReceiver(nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento));
        createRequest.setRegulation(ctFlussoRiversamento.getIdentificativoUnivocoRegolamento());
        createRequest.setRegulationDate(toLocalDateAtStartOfItalyOffset(ctFlussoRiversamento.getDataRegolamento()));
        createRequest.setBicCodePouringBank(ctFlussoRiversamento.getCodiceBicBancaDiRiversamento());
        createRequest.setTotPayments(ctFlussoRiversamento.getNumeroTotalePagamenti().longValue());
        createRequest.setSumPayments(ctFlussoRiversamento.getImportoTotalePagamenti().doubleValue());
        return createRequest;
    }

    public List<AddPaymentRequest> getAddPaymentRequestListChunked(List<CtDatiSingoliPagamenti> datiSingoliPagamentiList, int size) {
        List<List<CtDatiSingoliPagamenti>> datiSingoliPagamentiPartitioned = Lists.partition(datiSingoliPagamentiList, size);
        AtomicInteger index = new AtomicInteger(1);
        return datiSingoliPagamentiPartitioned.stream()
                .map(datiSingoliPagamentiListPartion -> {
                    AddPaymentRequest addPaymentRequest = new AddPaymentRequest();
                    addPaymentRequest.setPayments(datiSingoliPagamentiListPartion
                            .stream()
                            .map(v -> getPayment(v, index.getAndIncrement()))
                            .collect(Collectors.toList()));
                    return addPaymentRequest;
                })
                .collect(Collectors.toList());
    }

    private Sender getSender(NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest, CtFlussoRiversamento ctFlussoRiversamento){
        StTipoIdentificativoUnivoco tipoIdentificativoUnivoco = ctFlussoRiversamento.getIstitutoMittente().getIdentificativoUnivocoMittente().getTipoIdentificativoUnivoco();

        Sender sender = new Sender();
        sender.setType(typeMap.get(tipoIdentificativoUnivoco));
        sender.setId(ctFlussoRiversamento.getIstitutoMittente().getIdentificativoUnivocoMittente().getCodiceIdentificativoUnivoco());
        sender.setPspId(nodoInviaFlussoRendicontazioneRequest.getIdentificativoPSP());
        sender.setPspName(Optional.ofNullable(ctFlussoRiversamento.getIstitutoMittente().getDenominazioneMittente()).orElse("   ")); // whitespace to avoid pattern regex constraint
        sender.setPspBrokerId(nodoInviaFlussoRendicontazioneRequest.getIdentificativoIntermediarioPSP());
        sender.setChannelId(nodoInviaFlussoRendicontazioneRequest.getIdentificativoCanale());
        sender.setPassword(nodoInviaFlussoRendicontazioneRequest.getPassword());
        return sender;
    }

    private Receiver getReceiver(NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest, CtFlussoRiversamento ctFlussoRiversamento){
        String organizationId = nodoInviaFlussoRendicontazioneRequest.getIdentificativoDominio();
        String organizationName = FormatterUtil.sanitize(ctFlussoRiversamento.getIstitutoRicevente().getDenominazioneRicevente());

        Receiver receiver = new Receiver();
        receiver.setId(ctFlussoRiversamento.getIstitutoRicevente().getIdentificativoUnivocoRicevente().getCodiceIdentificativoUnivoco());
        receiver.setOrganizationId(organizationId);
        receiver.setOrganizationName(organizationName !=null ? organizationName : organizationId);
        return receiver;
    }

    private Payment getPayment(CtDatiSingoliPagamenti ctDatiSingoliPagamenti, int index){
        Payment payment = new Payment();
        payment.setIndex((long) index);
        payment.setIdTransfer(Optional.ofNullable(ctDatiSingoliPagamenti.getIndiceDatiSingoloPagamento()).orElse(1).longValue());
        payment.setIuv(ctDatiSingoliPagamenti.getIdentificativoUnivocoVersamento());
        payment.setIur(ctDatiSingoliPagamenti.getIdentificativoUnivocoRiscossione());
        payment.setPay(ctDatiSingoliPagamenti.getSingoloImportoPagato().doubleValue());
        payment.setPayDate(toLocalDateAtStartOfItalyOffset(ctDatiSingoliPagamenti.getDataEsitoSingoloPagamento()));
        payment.setPayStatus(payStatusMap.get(ctDatiSingoliPagamenti.getCodiceEsitoSingoloPagamento()));
        return payment;
    }
    
    /**
     * [PIDM-1734]
     * Converts an XMLGregorianCalendar datetime into an OffsetDateTime normalized to UTC.
     *
     * Conversion rules:
     * - if the source value contains an explicit timezone (for example 'Z' or '+06:00'),
     *   that timezone is respected and the value is converted to UTC
     * - if the source value does not contain timezone information, it is interpreted as
     *   local Italian time ('Europe/Rome') and then converted to UTC
     *
     * Examples:
     * - 2026-04-10T12:59:12.989Z      -> 2026-04-10T12:59:12.989Z
     * - 2026-04-10T12:59:12.989+06:00 -> 2026-04-10T06:59:12.989Z
     * - 2026-04-10T12:59:12.989       -> interpreted in Europe/Rome, then converted to UTC
     *
     *
     * @param value the source XMLGregorianCalendar datetime
     * @return the corresponding OffsetDateTime normalized to UTC, or null if the input is null
     */
    private OffsetDateTime toUtcOffsetDateTime(XMLGregorianCalendar value) {
        if (value == null) {
            return null;
        }

        if (value.getTimezone() != DatatypeConstants.FIELD_UNDEFINED) {
            return value.toGregorianCalendar()
                    .toZonedDateTime()
                    .withZoneSameInstant(UTC_ZONE)
                    .toOffsetDateTime();
        }

        LocalDate localDate = LocalDate.of(value.getYear(), value.getMonth(), value.getDay());

        LocalTime localTime = LocalTime.of(
                value.getHour(),
                value.getMinute(),
                value.getSecond(),
                value.getMillisecond() != DatatypeConstants.FIELD_UNDEFINED ? value.getMillisecond() * 1_000_000 : 0
        );

        return LocalDateTime.of(localDate, localTime)
                .atZone(ITALY_ZONE)
                .withZoneSameInstant(UTC_ZONE)
                .toOffsetDateTime();
    }
    
    /**
     * [PIDM-1734]
     * Converts a date-only XMLGregorianCalendar into an OffsetDateTime fixed at the start
     * of the day in the Italian time zone ('Europe/Rome').
     *
     * This helper is used for business date fields coming from FDR1, such as regulation date
     * and payment date, where the original local calendar day must be preserved.
     *
     * For these fields the goal is not UTC normalization, but day preservation.
     * Therefore the value is represented as start-of-day in Italian local time.
     *
     * Example:
     * input  -> 2026-03-24
     * output -> 2026-03-24T00:00:00+01:00
     *
     * @param value the source XMLGregorianCalendar date
     * @return the start of day in Europe/Rome for the given date, or null if the input is null
     */
    private OffsetDateTime toLocalDateAtStartOfItalyOffset(XMLGregorianCalendar value) {
        if (value == null) {
            return null;
        }

        LocalDate localDate = LocalDate.of(value.getYear(), value.getMonth(), value.getDay());

        return localDate.atStartOfDay(ITALY_ZONE).toOffsetDateTime();
    }
}
