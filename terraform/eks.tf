# LOCAL VARIABLES
locals {
  # Common tags to be assigned to all resources
  cluster_name              = var.optional_identifier != "" ? join("-", [local.common_name, "eks-cluster", var.optional_identifier]) : join("-", [local.common_name, "eks-cluster"])
  eks_cluster_sg_name       = var.optional_identifier != "" ? join("-", [local.common_name, "eks-cluster-security-group", var.optional_identifier]) : join("-", [local.common_name, "eks-cluster-security-group"])
  eks_worker_node_sg_name   = var.optional_identifier != "" ? join("-", [local.common_name, "eks-worker-node-security-group", var.optional_identifier]) : join("-", [local.common_name, "eks-worker-node-security-group"])
  eks_cluster_iam_role_name = var.optional_identifier != "" ? join("-", [local.common_name, "eks-cluster-iam-role", var.optional_identifier]) : join("-", [local.common_name, "eks-cluster-iam-role"])
  ebs_csi_driver_role_name  = var.optional_identifier != "" ? join("-", [local.common_name, "ebs-csi-driver-role", var.optional_identifier]) : join("-", [local.common_name, "ebs-csi-driver-role"])
  efs_csi_driver_role_name  = var.optional_identifier != "" ? join("-", [local.common_name, "efs-csi-driver-role", var.optional_identifier]) : join("-", [local.common_name, "efs-csi-driver-role"])
  worker_node_key_name      = var.optional_identifier != "" ? join("-", [local.common_name, "eks-worker-node-key", var.optional_identifier]) : join("-", [local.common_name, "eks-worker-node-key"])
}

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name    = local.cluster_name
  cluster_version = var.cluster_version

  cluster_endpoint_public_access = true

  cluster_addons = {
    coredns = {
      most_recent = true
    }
    kube-proxy = {
      most_recent = true
    }
    vpc-cni = {
      most_recent = true
      }
    aws-ebs-csi-driver = {
      most_recent = true
    }
    aws-efs-csi-driver = {
      most_recent = true
    }
  }

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  access_entries = {
    for username, user in var.eks_access_users : username => {
      principal_arn     = user.principal_arn
      kubernetes_groups = []
      policy_associations = {
        cluster_access = {
          policy_arn   = "arn:aws:eks::aws:cluster-access-policy/${user.policy}"
          access_scope = { type = "cluster" }
        }
      }
    }
  }

  enable_cluster_creator_admin_permissions = false  

  cluster_timeouts = {
    create = "30m"
    update = "30m"
    delete = "15m"
  }

  # Add managed node groups configuration
  eks_managed_node_groups = {
    ng1 = {
      name = "ng1"
      instance_types = ["t3.medium"]
      node_iam_role_additional_policies = [
        "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
      ]
      capacity_type = "SPOT"
      ebs_optimized = true
      disk_size = 40
      desired_size = 3
      min_size = 3 
      max_size = 4
      key_name = aws_key_pair.worker_nodes.key_name
      labels = {
      dedicated = "ng1"
      }

      # Attach the AmazonEBSCSIDriverPolicy policy to the node group in order to use EBS CSI driver
      iam_role_additional_policies = {
        AmazonEBSCSIDriverPolicy = "arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy",
        AmazonEFSCSIDriverPolicy = "arn:aws:iam::aws:policy/service-role/AmazonEFSCSIDriverPolicy"
      }

      tags = local.common_tags
    }
  }
}

resource "tls_private_key" "eks_worker_nodes_private_key" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "aws_key_pair" "worker_nodes" {
  key_name = local.worker_node_key_name

  public_key = tls_private_key.eks_worker_nodes_private_key.public_key_openssh
  # Generate and save private key () in current directory
  provisioner "local-exec" {
    command = <<-EOT
      echo '${tls_private_key.eks_worker_nodes_private_key.private_key_pem}' > '${local.worker_node_key_name}.pem'
      chmod 400 '${local.worker_node_key_name}.pem'
    EOT
  }
  tags = local.common_tags
}

