package com.ewp.crm.controllers.rest;

import com.ewp.crm.models.Client;
import com.ewp.crm.models.whatsapp.WhatsappMessage;
import com.ewp.crm.models.whatsapp.whatsappDTO.WhatsappAcknowledgement;
import com.ewp.crm.models.whatsapp.whatsappDTO.WhatsappAcknowledgementDTO;
import com.ewp.crm.repository.interfaces.ClientRepository;
import com.ewp.crm.repository.interfaces.WhatsappMessageRepository;
import com.ewp.crm.service.interfaces.SendNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/whatsapp")
public class WhatsappWebhook {
    private final WhatsappMessageRepository whatsappMessageRepository;
    private final ClientRepository clientRepository;
    private final SendNotificationService sendNotificationService;

    @Autowired
    public WhatsappWebhook(WhatsappMessageRepository whatsappMessageRepository, ClientRepository clientRepository, SendNotificationService sendNotificationService) {
        this.whatsappMessageRepository = whatsappMessageRepository;
        this.clientRepository = clientRepository;
        this.sendNotificationService = sendNotificationService;
    }

    @RequestMapping(method = RequestMethod.POST, produces = "application/json")
    public HttpStatus updateUser(@RequestBody WhatsappAcknowledgementDTO dto) {
        List<WhatsappMessage> whatsappMessages = dto.getMessages();
        List<WhatsappAcknowledgement> receipts = dto.getAck();
        if (whatsappMessages != null) {
            List<WhatsappMessage> newWhatsappMessages = whatsappMessages.stream()
                    .filter(x -> x.getChatId().matches("\\d*@c.*") && x.getBody().length() > 0 && x.getType().equals("chat"))
                    .peek(y -> y.setChatId(y.getChatId().replaceAll("\\D", "")))
                    .peek(z -> {
                        Client client = clientRepository.getClientByPhoneNumber(z.getChatId());
                        if (client == null) {
                            client = new Client(z.getSenderName(), z.getChatId(),z.getTime());
                            clientRepository.save(client);
                            sendNotificationService.sendNotificationsAllUsers(client);
                        }
                        z.setClient(client);
                    })
                    .collect(Collectors.toList());
            whatsappMessageRepository.saveAll(newWhatsappMessages);
        }
        if (receipts != null) {

            for (WhatsappAcknowledgement whatsappAcknowledgement : receipts
            ) {
                WhatsappMessage whatsappMessage = whatsappMessageRepository.findByMessageNumber(whatsappAcknowledgement.getQueueNumber());
                if (whatsappMessage != null && whatsappAcknowledgement.getStatus().getPriority() > 2) {
                    whatsappMessage.setSeen(true);
                    whatsappMessageRepository.save(whatsappMessage);
                }
            }
        }
        return HttpStatus.OK;

    }

}
