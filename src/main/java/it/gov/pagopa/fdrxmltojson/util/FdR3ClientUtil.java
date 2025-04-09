package it.gov.pagopa.fdrxmltojson.util;

import com.google.common.collect.Lists;
import it.gov.digitpa.schemas._2011.pagamenti.CtDatiSingoliPagamenti;
import it.gov.digitpa.schemas._2011.pagamenti.CtFlussoRiversamento;
import it.gov.digitpa.schemas._2011.pagamenti.StTipoIdentificativoUnivoco;
import it.gov.pagopa.pagopa_api.node.nodeforpsp.NodoInviaFlussoRendicontazioneRequest;
import org.openapitools.client.ApiClient;
import org.openapitools.client.api.InternalPspApi;
import org.openapitools.client.model.*;

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
        createRequest.setFdrDate(nodoInviaFlussoRendicontazioneRequest.getDataOraFlusso().toGregorianCalendar().toZonedDateTime().toOffsetDateTime());
        createRequest.setSender(getSender(nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento));
        createRequest.setReceiver(getReceiver(nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento));
        createRequest.setRegulation(ctFlussoRiversamento.getIdentificativoUnivocoRegolamento());
        createRequest.setRegulationDate(ctFlussoRiversamento.getDataRegolamento().toGregorianCalendar().toZonedDateTime().toLocalDate());
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
        sender.setPspName(Optional.ofNullable(ctFlussoRiversamento.getIstitutoMittente().getDenominazioneMittente()).orElse(""));
        sender.setPspBrokerId(nodoInviaFlussoRendicontazioneRequest.getIdentificativoIntermediarioPSP());
        sender.setChannelId(nodoInviaFlussoRendicontazioneRequest.getIdentificativoCanale());
        sender.setPassword(nodoInviaFlussoRendicontazioneRequest.getPassword());
        return sender;
    }

    private Receiver getReceiver(NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest, CtFlussoRiversamento ctFlussoRiversamento){
        String organizationId = nodoInviaFlussoRendicontazioneRequest.getIdentificativoDominio();
        String organizationName = ctFlussoRiversamento.getIstitutoRicevente().getDenominazioneRicevente();

        Receiver receiver = new Receiver();
        receiver.setId(ctFlussoRiversamento.getIstitutoRicevente().getIdentificativoUnivocoRicevente().getCodiceIdentificativoUnivoco());
        receiver.setOrganizationId(organizationId);
        receiver.setOrganizationName(organizationName!=null?organizationName:organizationId);
        return receiver;
    }

    private Payment getPayment(CtDatiSingoliPagamenti ctDatiSingoliPagamenti, int index){
        Payment payment = new Payment();
        payment.setIndex((long) index);
        payment.setIdTransfer(ctDatiSingoliPagamenti.getIndiceDatiSingoloPagamento().longValue());
        payment.setIuv(ctDatiSingoliPagamenti.getIdentificativoUnivocoVersamento());
        payment.setIur(ctDatiSingoliPagamenti.getIdentificativoUnivocoRiscossione());
        payment.setPay(ctDatiSingoliPagamenti.getSingoloImportoPagato().doubleValue());
        payment.setPayDate(ctDatiSingoliPagamenti.getDataEsitoSingoloPagamento().toGregorianCalendar().toZonedDateTime().toOffsetDateTime());
        payment.setPayStatus(payStatusMap.get(ctDatiSingoliPagamenti.getCodiceEsitoSingoloPagamento()));
        return payment;
    }
}
