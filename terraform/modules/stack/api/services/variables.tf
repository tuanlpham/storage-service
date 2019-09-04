# Shared infra

variable "subnets" {
  type = "list"
}

variable "vpc_id" {}

variable "cluster_id" {}

variable "namespace" {}
variable "namespace_id" {}

variable "nlb_arn" {}

data "aws_vpc" "vpc" {
  id = "${var.vpc_id}"
}

variable "desired_bags_api_count" {}
variable "desired_ingests_api_count" {}

# Ingests Endpoint

variable "ingests_container_image" {}
variable "ingests_container_port" {}

variable "ingests_nginx_container_image" {}
variable "ingests_nginx_container_port" {}

variable "ingests_listener_port" {}

variable "ingests_env_vars" {
  type = "map"
}

variable "ingests_env_vars_length" {}

# Bags Endpoint

variable "bags_container_image" {}
variable "bags_container_port" {}

variable "bags_nginx_container_image" {}
variable "bags_nginx_container_port" {}

variable "bags_listener_port" {}

variable "bags_env_vars" {
  type = "map"
}

variable "bags_env_vars_length" {}

variable "interservice_security_group_id" {}

variable "allow_ingests_publish_to_unpacker_topic_json" {}