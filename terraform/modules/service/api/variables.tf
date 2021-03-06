variable "subnets" {
  type = list(string)
}

variable "cluster_arn" {
}

variable "namespace" {
}

variable "namespace_id" {
}

variable "vpc_id" {
}

variable "container_image" {
}

variable "container_port" {
}

variable "nginx_container_image" {
}

variable "nginx_container_port" {
}

variable "security_group_ids" {
  type = list(string)
}

variable "env_vars" {
  type = map(string)
}

variable "lb_arn" {
}

variable "listener_port" {
}

variable "sidecar_cpu" {
  default = 512
}

variable "sidecar_memory" {
  default = 1024
}

variable "app_cpu" {
  default = 512
}

variable "app_memory" {
  default = 1024
}

variable "aws_region" {
  default = "eu-west-1"
}

variable "launch_type" {
  default = "FARGATE"
}

variable "desired_task_count" {
  default = 3
}

variable "use_fargate_spot_for_api" {
  type    = bool
  default = false
}
