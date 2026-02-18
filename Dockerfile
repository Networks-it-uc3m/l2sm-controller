FROM alexdecb/onos:2.7-latest


RUN apt-get update && \
    apt-get install -y wget ssh sshpass
    
COPY ./apps/vnets/target/vnets-app-1.0.oar .

COPY ./scripts/setup_controller.sh .
RUN chmod +x ./setup_controller.sh 

ENTRYPOINT ["./setup_controller.sh"]