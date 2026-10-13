export const recentFiles = [
  {
    id: 'recent-1',
    name: '2026年度经营规划.pptx',
    fileType: 'ppt',
    ownerName: '张伟',
    ownerAvatar: '张',
    modifiedAt: '2026-05-13T09:30:00',
    sizeBytes: 15938355,
    starred: true,
    description: '市场与经营评审资料'
  },
  {
    id: 'recent-2',
    name: '产品需求文档 v5.docx',
    fileType: 'word',
    ownerName: '李娜',
    ownerAvatar: '李',
    modifiedAt: '2026-05-13T08:45:00',
    sizeBytes: 2857000,
    description: '当前迭代功能定义'
  },
  {
    id: 'recent-3',
    name: '财务对账表 Q2.xlsx',
    fileType: 'excel',
    ownerName: '王强',
    ownerAvatar: '王',
    modifiedAt: '2026-05-12T18:20:00',
    sizeBytes: 4180000,
    starred: true,
    description: '财务中心共享表'
  },
  {
    id: 'recent-4',
    name: '企业宣传片终版.mp4',
    fileType: 'video',
    ownerName: '赵敏',
    ownerAvatar: '赵',
    modifiedAt: '2026-05-11T14:00:00',
    sizeBytes: 269169152,
    description: '品牌中心视频资产'
  },
  {
    id: 'recent-5',
    name: '合同模板.pdf',
    fileType: 'pdf',
    ownerName: '法务中心',
    ownerAvatar: '法',
    modifiedAt: '2026-05-10T10:30:00',
    sizeBytes: 1258291,
    description: '标准合同模板'
  },
  {
    id: 'recent-6',
    name: '产品资料库',
    fileType: 'folder',
    ownerName: '张伟',
    ownerAvatar: '张',
    modifiedAt: '2026-05-09T16:45:00',
    sizeBytes: null,
    starred: true,
    description: '产品中心文件夹'
  }
];

export const myFiles = [
  {
    id: 'my-1',
    name: 'CloudNext 版本计划.xlsx',
    fileType: 'excel',
    ownerName: '张伟',
    ownerAvatar: '张',
    modifiedAt: '2026-05-13T10:10:00',
    sizeBytes: 2382104,
    starred: true,
    description: '个人维护的版本推进清单'
  },
  {
    id: 'my-2',
    name: '客户调研访谈纪要.docx',
    fileType: 'word',
    ownerName: '张伟',
    ownerAvatar: '张',
    modifiedAt: '2026-05-12T19:40:00',
    sizeBytes: 912384,
    description: '最近一轮访谈总结'
  },
  {
    id: 'my-3',
    name: '季度复盘材料',
    fileType: 'folder',
    ownerName: '张伟',
    ownerAvatar: '张',
    modifiedAt: '2026-05-11T15:20:00',
    sizeBytes: null,
    description: '复盘附件与截图集合'
  },
  {
    id: 'my-4',
    name: '需求评审录屏.mp4',
    fileType: 'video',
    ownerName: '张伟',
    ownerAvatar: '张',
    modifiedAt: '2026-05-10T17:10:00',
    sizeBytes: 168820736,
    description: '5 月第 2 周评审录屏'
  }
];

export const sharedFiles = [
  {
    id: 'shared-1',
    name: '市场活动方案 2026.pptx',
    fileType: 'ppt',
    sharedByName: '李娜',
    sharedByAvatar: '李',
    modifiedAt: '2026-05-13T09:05:00',
    permissionLabel: '可编辑',
    expiresLabel: '长期有效',
    description: '市场团队共享给你的项目方案'
  },
  {
    id: 'shared-2',
    name: '客户合同审批清单.pdf',
    fileType: 'pdf',
    sharedByName: '法务中心',
    sharedByAvatar: '法',
    modifiedAt: '2026-05-12T13:20:00',
    permissionLabel: '只读',
    expiresLabel: '7 天后过期',
    description: '需配合法务进行确认'
  },
  {
    id: 'shared-3',
    name: '品牌图库',
    fileType: 'folder',
    sharedByName: '品牌中心',
    sharedByAvatar: '品',
    modifiedAt: '2026-05-11T11:00:00',
    permissionLabel: '可下载',
    expiresLabel: '长期有效',
    description: '统一品牌物料目录'
  },
  {
    id: 'shared-4',
    name: '对外演示视频.mp4',
    fileType: 'video',
    sharedByName: '王强',
    sharedByAvatar: '王',
    modifiedAt: '2026-05-09T16:30:00',
    permissionLabel: '可评论',
    expiresLabel: '30 天后过期',
    description: '商务演示公开视频'
  }
];

export const workspaces = [
  {
    id: 'ws-1',
    name: '品牌升级项目组',
    type: '共享工作区',
    ownerName: '李娜',
    ownerAvatar: '李',
    members: 18,
    updatedAt: '2026-05-13T10:15:00',
    description: '共享市场、设计和产品团队的跨部门协作文件。'
  },
  {
    id: 'ws-2',
    name: '华南销售中心',
    type: '部门空间',
    ownerName: '王强',
    ownerAvatar: '王',
    members: 32,
    updatedAt: '2026-05-12T16:05:00',
    description: '按部门组织销售材料、案例库与区域文件。'
  },
  {
    id: 'ws-3',
    name: '企业文件总库',
    type: '企业文件',
    ownerName: '系统管理员',
    ownerAvatar: '管',
    members: 245,
    updatedAt: '2026-05-11T09:40:00',
    description: '承接制度、模板、公共资料与长期沉淀内容。'
  },
  {
    id: 'ws-4',
    name: '研发技术委员会',
    type: '共享工作区',
    ownerName: '陈涛',
    ownerAvatar: '陈',
    members: 12,
    updatedAt: '2026-05-10T18:25:00',
    description: '技术标准、架构评审与方案沉淀空间。'
  }
];
