-- V5 角色精简（情侣/家庭模式）：去掉顾客概念，全员=店长+家人
UPDATE kitchen_members SET role = 'MEMBER' WHERE role = 'CUSTOMER';
