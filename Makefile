IMAGE ?= l2sm-controller
TAG ?= test
DOCKER ?= docker
MVN ?= mvn

VNETS_OAR := apps/vnets/target/vnets-app-1.0.oar
VLINKS_OAR := apps/vlinks/target/vlinks-app-1.0.oar

.PHONY: install test docker-build docker-push clean

install:
	$(MVN) clean install
	test -f $(VNETS_OAR)
	test -f $(VLINKS_OAR)

test:
	$(MVN) test

docker-build: install
	$(DOCKER) build -t $(IMAGE):$(TAG) .

docker-push: docker-build
	$(DOCKER) push $(IMAGE):$(TAG)

clean:
	$(MVN) clean
