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
    
    private static final ZoneId ITALY_ZONE = ZoneId.of("Europe/Rome");

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
        createRequest.setFdrDate(toOffsetDateTimePreservingSemantic(nodoInviaFlussoRendicontazioneRequest.getDataOraFlusso()));
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
     * Convert XMLGregorianCalendar to OffsetDateTime, preserving the original semantics of the date/time value.
     * - if PSP sends 'Z' or '+00:00', keep the original explicit timezone
     * - If the PSP does not send timezone, explicitly use 'Europe/Rome', as requested by the ticket
     * @param value The XMLGregorianCalendar to convert.
     * @return OffsetDateTime representing the same instant as the input XMLGregorianCalendar, preserving the original timezone semantics. Returns null if the input is null.
     */
    private OffsetDateTime toOffsetDateTimePreservingSemantic(XMLGregorianCalendar value) {
        if (value == null) {
            return null;
        }

        if (value.getTimezone() != DatatypeConstants.FIELD_UNDEFINED) {
            return value.toGregorianCalendar().toZonedDateTime().toOffsetDateTime();
        }

        LocalDate localDate = LocalDate.of(value.getYear(), value.getMonth(), value.getDay());

        LocalTime localTime = LocalTime.of(value.getHour(), value.getMinute(), value.getSecond());

        return LocalDateTime.of(localDate, localTime).atZone(ITALY_ZONE).toOffsetDateTime();
    }
    
    /**
     * [PIDM-1734]
     * Converts an XMLGregorianCalendar to an OffsetDateTime fixed at the start of the day
     * in the Italian time zone (Europe/Rome).
     *
     * This helper is used for date-only values coming from FDR1, so that transmission toward
     * FDR3 preserves the original local calendar date and avoids implicit timezone shifts.
     *
     * Example:
     * input  -> 2026-03-24
     * output -> 2026-03-24T00:00:00+01:00
     *
     * @param value the XMLGregorianCalendar source date
     * @return the start of day in Europe/Rome for the given date, or null if input is null
     */
    private OffsetDateTime toLocalDateAtStartOfItalyOffset(XMLGregorianCalendar value) {
        if (value == null) {
            return null;
        }

        LocalDate localDate = LocalDate.of(value.getYear(), value.getMonth(), value.getDay());

        return localDate.atStartOfDay(ITALY_ZONE).toOffsetDateTime();
    }
}
