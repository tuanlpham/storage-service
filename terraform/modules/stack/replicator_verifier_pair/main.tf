locals {
  java_opts_metrics_base = "-Dcom.amazonaws.sdk.enableDefaultMetrics=cloudwatchRegion=${var.aws_region}"
  java_opts_heap_size    = "-Xss6M -Xms2G -Xmx3G"
}

# bag_replicator

module "bag_replicator" {
  source = "../../service/scaling_worker"

  security_group_ids = var.security_group_ids

  cluster_name = var.cluster_name
  cluster_arn  = var.cluster_arn
  namespace_id = var.namespace_id
  subnets      = var.subnets
  service_name = local.bag_replicator_service_name

  env_vars = {
    queue_url               = module.bag_replicator_input_queue.url
    destination_bucket_name = var.bucket_name
    ingest_topic_arn        = var.ingests_topic_arn
    outgoing_topic_arn      = module.bag_replicator_output_topic.arn
    metrics_namespace       = local.bag_replicator_service_name
    operation_name          = "replicating to ${var.replica_display_name}"
    logstash_host           = var.logstash_host
    locking_table_name      = var.replicator_lock_table_name
    locking_table_index     = var.replicator_lock_table_index
    storage_provider        = var.storage_provider
    replica_type            = var.replica_type
    JAVA_OPTS               = "${local.java_opts_heap_size} ${local.java_opts_metrics_base},metricNameSpace=${local.bag_replicator_service_name}"
  }

  cpu    = 1024
  memory = 2048

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity

  container_image = var.bag_replicator_image
}

# bag_verifier

module "bag_verifier" {
  source = "../../service/scaling_worker"

  security_group_ids = var.security_group_ids

  cluster_name = var.cluster_name
  cluster_arn  = var.cluster_arn
  namespace_id = var.namespace_id
  subnets      = var.subnets
  service_name = local.bag_verifier_service_name

  env_vars = {
    queue_url          = module.bag_verifier_queue.url
    ingest_topic_arn   = var.ingests_topic_arn
    outgoing_topic_arn = module.bag_verifier_output_topic.arn
    metrics_namespace  = local.bag_verifier_service_name
    operation_name     = "verification (${var.replica_display_name})"
    logstash_host      = var.logstash_host
    JAVA_OPTS          = "${local.java_opts_heap_size} ${local.java_opts_metrics_base},metricNameSpace=${local.bag_verifier_service_name}"
  }

  cpu    = 2048
  memory = 4096

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity

  container_image = var.bag_verifier_image
}

