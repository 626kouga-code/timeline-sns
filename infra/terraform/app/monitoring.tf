# 監視（docs/10「検知」）。通知先は ../persistent の SNS トピック

locals {
  alarm_topic = local.persistent.alarm_topic_arn
}

resource "aws_cloudwatch_log_metric_filter" "server_errors" {
  name           = "${local.name}-5xx"
  log_group_name = aws_cloudwatch_log_group.backend.name
  pattern        = "{ $.status >= 500 }"
  metric_transformation {
    name          = "ServerErrors"
    namespace     = local.name
    value         = "1"
    default_value = "0"
  }
}

resource "aws_cloudwatch_log_metric_filter" "error_logs" {
  name           = "${local.name}-error-logs"
  log_group_name = aws_cloudwatch_log_group.backend.name
  pattern        = "{ $.log.level = \"ERROR\" }"
  metric_transformation {
    name          = "ErrorLogs"
    namespace     = local.name
    value         = "1"
    default_value = "0"
  }
}

locals {
  alarms = {
    "5xx" = {
      description = "5xx エラーが 5 分間に 5 件以上"
      namespace   = local.name
      metric      = "ServerErrors"
      statistic   = "Sum"
      period      = 300
      periods     = 1
      operator    = "GreaterThanOrEqualToThreshold"
      threshold   = 5
      dimensions  = {}
    }
    "error-logs" = {
      description = "ERROR ログが 5 分間に 1 件以上"
      namespace   = local.name
      metric      = "ErrorLogs"
      statistic   = "Sum"
      period      = 300
      periods     = 1
      operator    = "GreaterThanOrEqualToThreshold"
      threshold   = 1
      dimensions  = {}
    }
    "cpu" = {
      description = "ECS の CPU 使用率 80% 超が 10 分続く"
      namespace   = "AWS/ECS"
      metric      = "CPUUtilization"
      statistic   = "Average"
      period      = 300
      periods     = 2
      operator    = "GreaterThanThreshold"
      threshold   = 80
      dimensions  = { ClusterName = aws_ecs_cluster.main.name, ServiceName = aws_ecs_service.backend.name }
    }
    "memory" = {
      description = "ECS のメモリ使用率 85% 超が 10 分続く"
      namespace   = "AWS/ECS"
      metric      = "MemoryUtilization"
      statistic   = "Average"
      period      = 300
      periods     = 2
      operator    = "GreaterThanThreshold"
      threshold   = 85
      dimensions  = { ClusterName = aws_ecs_cluster.main.name, ServiceName = aws_ecs_service.backend.name }
    }
    "db-connections" = {
      # db.t4g.micro（1GB）の max_connections は 100 前後。その 80%
      description = "DB の接続数が 80 を超えた"
      namespace   = "AWS/RDS"
      metric      = "DatabaseConnections"
      statistic   = "Maximum"
      period      = 300
      periods     = 1
      operator    = "GreaterThanThreshold"
      threshold   = 80
      dimensions  = { DBInstanceIdentifier = aws_db_instance.main.identifier }
    }
    "db-storage" = {
      description = "DB のストレージの残りが 20%（4GB）未満"
      namespace   = "AWS/RDS"
      metric      = "FreeStorageSpace"
      statistic   = "Minimum"
      period      = 300
      periods     = 1
      operator    = "LessThanThreshold"
      threshold   = 4 * 1024 * 1024 * 1024
      dimensions  = { DBInstanceIdentifier = aws_db_instance.main.identifier }
    }
    "healthy-hosts" = {
      # 作った直後・入れ替え中の起動待ち（最大 3 分）では鳴らないよう、5 分続いたら
      description = "正常なタスクが 0 のまま 5 分続く"
      namespace   = "AWS/ApplicationELB"
      metric      = "HealthyHostCount"
      statistic   = "Minimum"
      period      = 60
      periods     = 5
      operator    = "LessThanThreshold"
      threshold   = 1
      dimensions = {
        LoadBalancer = aws_lb.main.arn_suffix
        TargetGroup  = aws_lb_target_group.backend.arn_suffix
      }
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "this" {
  for_each = local.alarms

  alarm_name          = "${local.name}-${each.key}"
  alarm_description   = each.value.description
  namespace           = each.value.namespace
  metric_name         = each.value.metric
  statistic           = each.value.statistic
  period              = each.value.period
  evaluation_periods  = each.value.periods
  comparison_operator = each.value.operator
  threshold           = each.value.threshold
  dimensions          = each.value.dimensions
  treat_missing_data  = "notBreaching"
  # 鳴ったときと、収まったときに通知する
  alarm_actions = [local.alarm_topic]
  ok_actions    = [local.alarm_topic]
}
