IMAGE ?= l2sm-controller
TAG ?= latest
DOCKER ?= docker
MVN ?= mvn

.PHONY: install test docker-build docker-push

install:
	$(MVN) clean install

test:
	$(MVN) test

docker-build: install
	$(DOCKER) build -t $(IMAGE):$(TAG) .

docker-push:
	$(DOCKER) push $(IMAGE):$(TAG)
